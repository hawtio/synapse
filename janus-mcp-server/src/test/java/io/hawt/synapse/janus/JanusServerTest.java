package io.hawt.synapse.janus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JanusServerTest {

	@TestHTTPResource
    protected URI testUri;

	 // Inject the actual port being used for this test run
//    @ConfigProperty(name = "quarkus.http.test-port")
//    String testPort;
	@ConfigProperty(name="quarkus.mcp.server.sse.root-path")
	String mcpPath;
	
	@BeforeEach
    void setTestUri() {
		System.out.println("testUri: " + testUri);
        McpAssured.baseUri = testUri;
    }

    @Test
    public void testReadAttributeTool() {
        McpStreamableTestClient client = McpAssured
        		.newStreamableClient()
        		.setMcpPath(mcpPath)
        		.build()
        		.connect();

        client.when()
                .toolsCall(
                		"readAttribute",
                		Map.of(
                				"namespace", "hawtio-dev",
                				"podName", "hawtio-example-999",
                				"mbean", "org.apache.camel:context=MyCamel,type=context,name=\"MyCamel\"",
                				"attribute", "CamelId"), r -> { 
                			assertEquals("Spaces are better for indentation.", r.content().get(0).asText().text());
                		})
                .thenAssertResults(); 
    }
}
