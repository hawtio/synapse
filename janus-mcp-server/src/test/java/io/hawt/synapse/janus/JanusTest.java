package io.hawt.synapse.janus;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;

public abstract class JanusTest {

    protected static final Integer JOLOKIA_CONTAINER_PORT = 8778;

    protected static final String NAMESPACE = "hawtio-dev";

    protected static final String POD_NAME = "hawtio-example-999";

    protected static final String POD_IP = "10.0.0.10";

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

}
