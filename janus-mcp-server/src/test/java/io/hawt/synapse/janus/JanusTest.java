package io.hawt.synapse.janus;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;

public abstract class JanusTest implements TestConstants {

    protected static ContainerBuilder buildContainer(boolean addJolokiaPort) {
        ContainerBuilder builder = new ContainerBuilder();
        builder.withImage("image-registry.my-registry.org/hawtio-dev/hawtio-example").withName("spring-boot")
                .addNewPort().withContainerPort(8080).withName("http").withProtocol("TCP").endPort().addNewPort()
                .withContainerPort(9779).withName("prometheus").withProtocol("TCP").endPort();

        if (addJolokiaPort) {
            builder.addNewPort().withContainerPort(JOLOKIA_CONTAINER_PORT).withName("jolokia").withProtocol("TCP")
                    .endPort();
        }

        return builder;
    }

    protected static PodSpecBuilder buildPodSpec(int containers, int jolokiaContainerIndex) {
        List<Container> containerList = new ArrayList<Container>();
        for (int i = 0; i < containers; ++i) {
            containerList.add(buildContainer((i + 1) == jolokiaContainerIndex).build());
        }

        PodSpecBuilder builder = new PodSpecBuilder();
        builder.withContainers(containerList);
        return builder;
    }

    protected static PodBuilder buildPod(int containers, int jolokiaContainerIndex) {
        PodSpecBuilder specBuilder = buildPodSpec(containers, jolokiaContainerIndex);

        PodBuilder builder = new PodBuilder().withNewMetadata().withName(POD_NAME).withNamespace(NAMESPACE)
                .endMetadata().withSpec(specBuilder.build()).withNewStatus().withPhase("Running").withPodIP(POD_IP)
                .endStatus();

        return builder;
    }

    protected static TokenReview tokenReview(String reviewName, String token, boolean authenticated) {
        return new TokenReviewBuilder()
                .withNewMetadata()
                    .withName(reviewName)
                .endMetadata()
                .withNewSpec()
                    .withToken(token)
                .endSpec()
                .withNewStatus()
                    .withAuthenticated(authenticated)
                .endStatus()
                .build();
    }
}
