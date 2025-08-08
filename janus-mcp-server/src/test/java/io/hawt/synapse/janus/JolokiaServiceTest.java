package io.hawt.synapse.janus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class JolokiaServiceTest {

	private static final Integer JOLOKIA_CONTAINER_PORT = 8778;

	@Inject
	private JolokiaService jolokiaService;

	private String namespace = "hawtio-dev";

	private String podName = "hawtio-example-999";

	private String podIP = "10.0.0.10";

	@BeforeEach
	public void setTestUri() {

	}

	@Test
	public void testConnectionURL_nullPod() {
		assertThrows(Exception.class, () -> {
			jolokiaService.connectionURL(null);
		});
	}

	@Test
	public void testConnectionURL_validPod() {
		Pod validPod = new PodBuilder()
				.withNewMetadata()
					.withName(podName)
					.withNamespace(namespace)
				.endMetadata()
				.withNewSpec()
					.addNewContainer()
						.withImage("image-registry.my-registry.org/hawtio-dev/hawtio-example")
						.withName("spring-boot")
						.addNewPort()
							.withContainerPort(8080)
							.withName("http")
							.withProtocol("TCP")
						.endPort()
						.addNewPort()
							.withContainerPort(9779)
							.withName("prometheus")
							.withProtocol("TCP")
		                .endPort()
		                .addNewPort()
							.withContainerPort(JOLOKIA_CONTAINER_PORT)
							.withName("jolokia")
							.withProtocol("TCP")
						.endPort()
					.endContainer()
				.endSpec()
				.withNewStatus()
					.withPhase("Running")
					.withPodIP(podIP)
				.endStatus()
				.build();

		System.out.println(validPod.toString());
		assertDoesNotThrow(() -> {

			URL url = jolokiaService.connectionURL(validPod);
			assertNotNull(url, "The returned URL should not be null");
			assertEquals(String.format("%s://%s:%d%s",
					JolokiaService.DEFAULT_JOLOKIA_PROTOCOL,
					podIP,
					JOLOKIA_CONTAINER_PORT,
					JolokiaService.DEFAULT_JOLOKIA_PATH),
				url.toString());
		
		}, "connectionURL should not throw an exception for a valid pod");
	}
}
