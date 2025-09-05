package io.hawt.synapse.janus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.KubernetesServer;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;

@QuarkusTest
@WithKubernetesTestServer
public class JanusServerTest extends JanusTest {
    private static final Logger LOG = Logger.getLogger(JanusServerTest.class);

    @TestHTTPResource
    private URI testUri;

    @KubernetesTestServer
    private KubernetesServer mockServer;

    @Inject
    private KubernetesClient kClient;

    @InjectMock
    HttpServerRequest mockRequest;

    @ConfigProperty(name = "quarkus.mcp.server.sse.root-path")
    private String mcpPath;

    @BeforeEach
    public void setup() {
        mockServer.getKubernetesMockServer().reset();
        mockServer.getKubernetesMockServer().clearExpectations();

        McpAssured.baseUri = testUri;
        Mockito.when(mockRequest.getHeader("Authorization")).thenReturn(BEARER_TOKEN);

        mockServer.getKubernetesMockServer().clearExpectations();

        // Tailor the server to expect token reviews and return success only when BEARER_TOKEN is used
        mockServer.expect()
            .post()
            .withPath("/apis/authentication.k8s.io/v1/tokenreviews")
            .andReply(201, request -> {
                // Deserialize the request body
                TokenReview receivedReview = Serialization.unmarshal(request.getBody().readUtf8(), TokenReview.class);

                // Check the token in the body and return the correct response
                if (BEARER_TOKEN.equals(receivedReview.getSpec().getToken())) {
                    LOG.info("TokenReview is good!");
                    return tokenReview(receivedReview.getMetadata().getName(), receivedReview.getSpec().getToken(), true);
                } else {
                    LOG.debugf("Token '%s' in TokenReview is bad! ", receivedReview.getSpec().getToken());
                    return tokenReview(receivedReview.getMetadata().getName(), receivedReview.getSpec().getToken(), false);
                }
            })
            .always();

        // Build the valid pod for each test
        Pod validPod = buildPod(1, 1).build();
        kClient.pods().resource(validPod).create();
    }

    /*
     * General failure-mode tests
     */
    @TestFactory
    @DisplayName("General Failure-Mode Tests")
    Stream<DynamicTest> GeneralFailureTests() {
        List<GeneralTestCase> testCases = List.of(
                GeneralTestCase.builder("with a null token")
                    .withBearerToken(null)
                    .exceptionResult("Failed to validate authentication: Authentication failed: No bearer token provided.")
                    .build(),
                GeneralTestCase.builder("with an empty token")
                    .withBearerToken("")
                    .exceptionResult("Failed to validate authentication: Authentication failed: No bearer token provided.")
                    .build(),
                GeneralTestCase.builder("with a malformed token")
                    .withBearerToken("not-a-valid-token")
                    .exceptionResult("Failed to validate authentication: Token validation failed. Could not authenticate.")
                    .build(),
                GeneralTestCase.builder("with an invalid namespace")
                    .withNamespace(NAMESPACE + "-invalid")
                    .exceptionResult("Pod not found: hawtio-example-999 in namespace: hawtio-dev-invalid")
                    .build(),
                GeneralTestCase.builder("with an invalid pod")
                    .withPodName("invalid-pod")
                    .exceptionResult("Pod not found: invalid-pod in namespace: hawtio-dev")
                    .build()
        );

        List<String> toolNames = List.of("version", "readAttribute");

        return
                toolNames.stream().flatMap(toolName ->
                    testCases.stream().map(testCase -> {
                        return dynamicTest(toolName + "_" + testCase.name(), () -> {
                            Mockito.when(mockRequest.getHeader("Authorization")).thenReturn(testCase.bearerToken());

                            McpStreamableTestClient mcpClient = McpAssured.newStreamableClient().setMcpPath(mcpPath).build().connect();
                            mcpClient.when().toolsCall(toolName, testCase.map(), r -> {
                                assertTrue(r.isError());
                                assertEquals(testCase.exceptionResult(), r.content().get(0).asText().text());
                            }).thenAssertResults();
                        });
                    })
                );
    }


    @Test
    public void testReadAttributeTool_ValidPod() {
        Mockito.when(mockRequest.getHeader("Authorization")).thenReturn(TestConstants.bearerTokenHeader(BEARER_TOKEN));
        McpStreamableTestClient mcpClient = McpAssured.newStreamableClient().setMcpPath(mcpPath).build().connect();

        mcpClient.when().toolsCall("readAttribute",
                Map.of("namespace", NAMESPACE, "podName", POD_NAME, "mbean", MBEAN, "attribute", MBEAN_ATTRIBUTE), r -> {
                    assertFalse(r.isError());
                    assertEquals("Mock value for " + MBEAN_ATTRIBUTE + " on pod " + POD_NAME + " is 54321.",
                            r.content().get(0).asText().text());
                }).thenAssertResults();
    }
}
