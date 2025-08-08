package io.hawt.synapse.janus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;

@WithKubernetesTestServer
@QuarkusTest
@Disabled
public class JanusServerTest {

	@TestHTTPResource
    protected URI testUri;
	
	@KubernetesTestServer
    KubernetesServer mockServer;
    @Inject
    KubernetesClient client;

	 // Inject the actual port being used for this test run
//    @ConfigProperty(name = "quarkus.http.test-port")
//    String testPort;
	@ConfigProperty(name="quarkus.mcp.server.sse.root-path")
	private String mcpPath;

	private String namespace = "hawtio-dev";

	private String podName = "hawtio-example-999";
	
	private String podIP = "10.0.0.10";

	private String mbean = "org.apache.camel:context=MyCamel,type=context,name=\"MyCamel\"";

	private String mbeanAttribute = "CamelId";

	@BeforeEach
    public void setTestUri() {
		System.out.println("testUri: " + testUri);
        McpAssured.baseUri = testUri;
    }

    @Test
    public void testReadAttributeTool_InvalidPod() {
        McpStreamableTestClient client = McpAssured
        		.newStreamableClient()
        		.setMcpPath(mcpPath)
        		.build()
        		.connect();
        
        String invalidPodName = podName + "INVALID";

        client.when()
                .toolsCall(
                		"readAttribute",
                		Map.of(
                			"namespace", namespace,
                			"podName", invalidPodName,
                			"mbean", mbean,
                			"attribute", mbeanAttribute
                		),
                		r -> {
                			assertTrue(r.isError());
                			assertEquals("Pod not found: " + invalidPodName + " in namespace: " + namespace,
                					r.content().get(0).asText().text());
                		}
                )
                .thenAssertResults(); 
    }

    @Test
    public void testReadAttributeTool_ValidPod() {

    	Pod validPod = new PodBuilder()
    			.withNewMetadata()
    				.withName(podName)
    				.withNamespace(namespace)
    			.endMetadata()
    			.withNewStatus()
    				.withPhase("Running")
    				.withPodIP(podIP)
    			.endStatus()
    		.build();
    	client.pods().resource(validPod).create();

        McpStreamableTestClient client = McpAssured
        		.newStreamableClient()
        		.setMcpPath(mcpPath)
        		.build()
        		.connect();

        client.when()
                .toolsCall(
                		"readAttribute",
                		Map.of(
                			"namespace", namespace,
                			"podName", podName,
                			"mbean", mbean,
                			"attribute", mbeanAttribute
                		),
                		r -> {
                			assertFalse(r.isError());
                			assertEquals("Mock value for " + mbeanAttribute + " on pod " + podName + " is 54321.",
                					r.content().get(0).asText().text());
                		}
                )
                .thenAssertResults(); 
    }
}
