package io.hawt.synapse.janus;

import java.net.URL;
import java.util.Optional;

import javax.naming.AuthenticationException;

import org.jboss.logging.Logger;
import org.jolokia.json.JSONObject;

import io.fabric8.kubernetes.api.model.Pod; // The Fabric8 Pod model
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JanusServer {

    private static final Logger LOG = Logger.getLogger(JanusServer.class);

    @Inject
    HttpServerRequest request; // For getting the Authorization header

    // Inject the KubernetesClient. Quarkus will configure this automatically
    // to talk to the OpenShift cluster it's running in.
    @Inject
    private KubernetesClient k8sClient;

    @Inject
    private JolokiaConnection jolokiaConnection;

    @Inject
    JolokiaServiceFactory jolokiaServiceFactory;

    /**
     * Extracts the bearer token from the Authorization header.
     * @return The token string, or null if not present.
     */
    private String getBearerToken() {
        final String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private String validateToken(String namespace, String podName) throws Exception {
        String token = getBearerToken();
        if (token == null || token.length() == 0) {
            throw new AuthenticationException("Authentication failed: No bearer token provided.");
        }

        // Create a TokenReview object with the user's token
        TokenReview review = new TokenReviewBuilder()
                .withNewMetadata()
                    .withGenerateName(token)
                .endMetadata()
                .withNewSpec()
                    .withToken(token)
                .endSpec()
                .build();

        // Submit the review to the Kubernetes API server
        TokenReview result = k8sClient.authentication().v1().tokenReviews().create(review);

        // Check the status of the review
        TokenReviewStatus status = result.getStatus();
        if (status == null || ! status.getAuthenticated()) {
            // Token is invalid or unauthenticated
            String error = (status != null) ? status.getError() : "Unknown";
            LOG.warnf("Token validation failed: %s", error);
            throw new AuthenticationException("Token validation failed. Could not authenticate.");
        }

        return token;
    }

    private JolokiaService getJolokiaService(URL jolokiaUrl, String authToken) throws Exception {
        if (jolokiaUrl == null) {
            throw new Exception("Cannot instantiate a jolokia service as the url is null");
        }

        if (authToken == null) {
            throw new Exception("Cannot instantiate a jolokia service as the authToken is null");
        }
        return jolokiaServiceFactory.createService(jolokiaUrl.toString(), authToken);
    }

    /**
     * Reads the version of the jolokia server from a uniquely identified pod.
     */
    @Tool(description = "Reads the version of the jolokia server attached to a specific pod in a specific namespace.")
    public ToolResponse version(@ToolArg(description = "The Kubernetes namespace of the target pod") String namespace,
            @ToolArg(description = "The name of the target pod") String podName, McpLog log) {

        // --- Security Check ---
        String authToken = null;
        try {
            authToken = this.validateToken(namespace, podName);
        } catch (Exception ex) {
            LOG.error("Pod " + podName + " produced an error while validation authentication", ex);
            return ToolResponse.error("Failed to validate authentication: " + ex.getMessage());
        }

        LOG.info("Received valid authorization token: " + authToken);

        // --- Dynamic Service Discovery Logic ---
        Pod targetPod = null;
        String podIp = null;
        try {
            // Use the Kubernetes client to fetch the pod object.
            LOG.debugf("Fetching the pod with name '%s' from namespace '%s' in cluster", podName, namespace);
            targetPod = k8sClient.pods().inNamespace(namespace).withName(podName).get();
            if (targetPod == null) {
                return ToolResponse.error("Pod not found: " + podName + " in namespace: " + namespace);
            }

            // Get the pod's internal IP address.
            LOG.debugf("Fetching the ip address for pod with name '%s' from namespace '%s' in cluster", podName,
                    namespace);
            podIp = targetPod.getStatus().getPodIP();
            if (podIp == null || podIp.isEmpty()) {
                return ToolResponse
                        .error("Pod " + podName + " does not have an IP address yet. It may still be starting.");
            }
        } catch (Exception ex) {
            // Catch any potential exceptions from the Kubernetes API client.
            LOG.error("Pod " + podName + " produced an error while querying the kubernetes cluster", ex);
            return ToolResponse.error("Failed to query Kubernetes API: " + ex.getMessage());
        }

        LOG.debugf(String.format("The pod with name '%s' from namespace '%s' has the ip address '%s'", podName,
                namespace, podIp));

        // Construct the Jolokia URL on the fly.
        URL jolokiaUrl = null;
        try {
            jolokiaUrl = jolokiaConnection.connectionURL(targetPod);
            if (jolokiaUrl == null) {
                return ToolResponse.success(String.format(
                        "The pod '%s' was found but does not expose a Jolokia port, so it is not a pod of interest.",
                        podName));
            }

            LOG.debugf("Pod '%s' has jolokia url of %s'", podName, jolokiaUrl);
        } catch (Exception ex) {
            LOG.error("Pod " + podName + " produced an error while deriving the url to the jolokia service", ex);
            return ToolResponse
                    .error(String.format("Pod '%s' produced an error while deriving the url to the jolokia service: %s",
                            podName, ex.getMessage()));
        }

        try {
            Optional<JSONObject> versionOptional = getJolokiaService(jolokiaUrl, authToken).version();
            if (versionOptional.isPresent()) {
                JSONObject version = versionOptional.get();
                LOG.info(String.format("Successfully retrieved Jolokia version from pod %s", podName));

                /*
                 * Return the JSON data as an embedded resource. The LLM can parse this much
                 * better than a plain text string.
                 */

                // Create a descriptive URI for the dynamically generated data.
                String resourceUri = "jolokia://" + podName + "/version";

                // 2. Create the TextResourceContents with the new URI parameter.
                TextResourceContents textResource = new TextResourceContents(resourceUri, version.toString(),
                        "application/json");
                EmbeddedResource embeddedResource = new EmbeddedResource(textResource);
                return ToolResponse.success(embeddedResource);
            } else {
                // The tool worked, but Jolokia returned nothing.
                LOG.warnf("Pod %s returned an empty optional for Jolokia version.", podName);
                return ToolResponse.success(String.format(
                        "Successfully connected to pod '%s', but no version information was returned by the Jolokia endpoint.",
                        podName));
            }

        } catch (Exception ex) {
            LOG.error("Pod " + podName + " produced an error while querying the application's jolokia service", ex);
            return ToolResponse.error(
                    String.format("Pod '%s' produced an error while quering the application's jolokia service: %s",
                            podName, ex.getMessage()));
        }
    }

    /**
     * Reads a specific JMX MBean attribute from a uniquely identified pod.
     */
    @Tool(description = "Read an attribute from a given MBean on a specific pod in a specific namespace.")
    public ToolResponse readAttribute(
            @ToolArg(description = "The Kubernetes namespace of the target pod") String namespace,
            @ToolArg(description = "The name of the target pod") String podName,
            @ToolArg(description = "The JMX MBean ObjectName") String mbean,
            @ToolArg(description = "The name of the attribute to read") String attribute, McpLog log) {

        // --- Security Check ---
        String authToken = null;
        try {
            authToken = this.validateToken(namespace, podName);
        } catch (Exception ex) {
            LOG.error("Pod " + podName + " produced an error while validation authentication", ex);
            return ToolResponse.error("Failed to validate authentication: " + ex.getMessage());
        }

        log.info("Received valid authorization token: " + authToken);

        // --- Dynamic Service Discovery Logic ---
        try {
            // Use the Kubernetes client to fetch the pod object.
            LOG.debugf("Fetching the pod with name '%s' from namespace '%s' in cluster", podName, namespace);
            Pod targetPod = k8sClient.pods().inNamespace(namespace).withName(podName).get();

            if (targetPod == null) {
                return ToolResponse.error("Pod not found: " + podName + " in namespace: " + namespace);
            }

            // Get the pod's internal IP address.
            LOG.debugf("Fetching the ip address for pod with name '%s' from namespace '%s' in cluster", podName,
                    namespace);
            String podIp = targetPod.getStatus().getPodIP();
            if (podIp == null || podIp.isEmpty()) {
                return ToolResponse
                        .error("Pod " + podName + " does not have an IP address yet. It may still be starting.");
            }

            LOG.debugf(String.format("The pod with name '%s' from namespace '%s' has the ip address '%s'", podName,
                    namespace, podIp));

            // Construct the Jolokia URL on the fly.
            URL jolokiaUrl = null;
            try {
                jolokiaUrl = jolokiaConnection.connectionURL(targetPod);
            } catch (Exception ex) {
                LOG.error("Pod " + podName + " produced an error while deriving the url to the jolokia service", ex);
                return ToolResponse.error(
                        String.format("Pod '%s' produced an error while deriving the url to the jolokia service: %s",
                                podName, ex.getMessage()));
            }

            if (jolokiaUrl == null) {
                return ToolResponse.success(String.format(
                        "The pod '%s' was found but does not expose a Jolokia port, so it is not a pod of interest.",
                        podName));
            }

            LOG.debugf("Pod '%s' has jolokia url of %s'", podName, jolokiaUrl);

            // Placeholder for the actual Jolokia client logic.
            // Here, we would use an HTTP client to connect to the dynamically
            // constructed 'jolokiaUrl' and perform the JMX read operation,
            // passing along the auth token.
            String mockResponse = String.format("Mock value for %s on pod %s is 54321.", attribute, podName);
            log.info(mockResponse);
            return ToolResponse.success(mockResponse);

        } catch (Exception e) {
            // Catch any potential exceptions from the Kubernetes API client.
            return ToolResponse.error("Failed to query Kubernetes API: " + e.getMessage());
        }
    }
}
