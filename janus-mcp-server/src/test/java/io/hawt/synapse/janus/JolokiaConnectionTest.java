package io.hawt.synapse.janus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class JolokiaConnectionTest extends JanusTest {

    @Inject
    private JolokiaConnection jolokiaService;

    @Test
    public void testConnectionURL_nullPod() {
        assertThrows(Exception.class, () -> {
            jolokiaService.connectionURL(null);
        });
    }

    @Test
    public void testConnectionURL_otherPod() {
        Pod pod = buildPod(1, -1).build();

        assertDoesNotThrow(() -> {

            URL url = jolokiaService.connectionURL(pod);
            assertNull(url, "The returned URL should be null as no jolokia port");

        }, "connectionURL should not throw an exception for a valid pod");
    }

    @Test
    public void testConnectionURL_jolokiaPod() {
        Pod pod = buildPod(1, 1).build();

        assertDoesNotThrow(() -> {

            URL url = jolokiaService.connectionURL(pod);
            assertNotNull(url, "The returned URL should not be null");
            assertEquals(String.format("%s://%s:%d%s", JolokiaConnection.DEFAULT_JOLOKIA_PROTOCOL, POD_IP,
                    JOLOKIA_CONTAINER_PORT, JolokiaConnection.DEFAULT_JOLOKIA_PATH), url.toString());

        }, "connectionURL should not throw an exception for a valid pod");
    }

    @Test
    public void testConnectionURL_multiContainerJolokiaPod() {
        Pod pod = buildPod(2, 2).build();

        assertDoesNotThrow(() -> {

            URL url = jolokiaService.connectionURL(pod);
            assertNotNull(url, "The returned URL should not be null");
            assertEquals(String.format("%s://%s:%d%s", JolokiaConnection.DEFAULT_JOLOKIA_PROTOCOL, POD_IP,
                    JOLOKIA_CONTAINER_PORT, JolokiaConnection.DEFAULT_JOLOKIA_PATH), url.toString());

        }, "connectionURL should not throw an exception for a valid pod");
    }
}
