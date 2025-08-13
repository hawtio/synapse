package io.hawt.synapse.janus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

@QuarkusTest
@WithKubernetesTestServer
public class JanusServerTest extends JanusTest {

    @TestHTTPResource
    protected URI testUri;

    @KubernetesTestServer
    KubernetesServer mockServer;

    @Inject
    KubernetesClient kClient;

    @ConfigProperty(name = "quarkus.mcp.server.sse.root-path")
    private String mcpPath;

    private String mbean = "org.apache.camel:context=MyCamel,type=context,name=\"MyCamel\"";

    private String mbeanAttribute = "CamelId";

    @BeforeEach
    public void setTestUri() {
        System.out.println("testUri: " + testUri);
        McpAssured.baseUri = testUri;
    }

    @Test
    @TestSecurity(user = "test-user", roles = { "viewer" })
    public void testReadAttributeTool_InvalidPod() {
        McpStreamableTestClient mcpClient = McpAssured.newStreamableClient().setMcpPath(mcpPath).build().connect();

        String invalidPodName = POD_NAME + "INVALID";

        mcpClient.when().toolsCall("readAttribute",
                Map.of("namespace", NAMESPACE, "podName", invalidPodName, "mbean", mbean, "attribute", mbeanAttribute),
                r -> {
                    assertTrue(r.isError());
                    assertEquals("Pod not found: " + invalidPodName + " in namespace: " + NAMESPACE,
                            r.content().get(0).asText().text());
                }).thenAssertResults();
    }

    @Test
    @TestSecurity(user = "test-user", roles = { "viewer" })
    public void testReadAttributeTool_ValidPod() {

        Pod validPod = buildPod(1, 1).build();
        kClient.pods().resource(validPod).create();

        McpStreamableTestClient mcpClient = McpAssured.newStreamableClient().setMcpPath(mcpPath).build().connect();

        mcpClient.when().toolsCall("readAttribute",
                Map.of("namespace", NAMESPACE, "podName", POD_NAME, "mbean", mbean, "attribute", mbeanAttribute), r -> {
                    assertFalse(r.isError());
                    assertEquals("Mock value for " + mbeanAttribute + " on pod " + POD_NAME + " is 54321.",
                            r.content().get(0).asText().text());
                }).thenAssertResults();
    }
}
