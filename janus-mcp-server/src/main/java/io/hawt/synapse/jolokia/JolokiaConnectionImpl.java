package io.hawt.synapse.jolokia;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.hawt.synapse.janus.JolokiaConnection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JolokiaConnectionImpl implements JolokiaConnection {

    protected static final String DEFAULT_JOLOKIA_PROTOCOL = "https";
    protected static final String DEFAULT_JOLOKIA_PATH = "/jolokia/";

    @Inject
    ObjectMapper objectMapper;

    private static final String JOLOKIA_PORT_NAME = "jolokia";
    private static final String JOLOKIA_PORT_QUERY = "$.spec.containers[*].ports[?(@.name=='" + JOLOKIA_PORT_NAME
            + "')]";

    private static final Logger LOG = Logger.getLogger(JolokiaConnection.class);

    @Override
    public boolean hasJolokiaPort(Pod pod) throws JsonProcessingException {
        if (pod == null) {
            return false;
        }

        String podAsJson = objectMapper.writeValueAsString(pod);
        List<Object> foundPorts = JsonPath.read(podAsJson, JOLOKIA_PORT_QUERY);
        LOG.debugf("Found %d jolokia ports in pod '%s'", foundPorts.size(), pod.getFullResourceName());
        return !foundPorts.isEmpty();
    }

    private Integer jolokiaContainerPort(Container container) {
        List<ContainerPort> ports = container.getPorts();

        LOG.debugf("jolokiaContainerPorts identified: %s", ports);

        ContainerPort containerPort = ports.stream().filter(port -> JOLOKIA_PORT_NAME.equals(port.getName()))
                .findFirst().orElse(null);
        if (containerPort == null) {
            LOG.debugf("No jolokia port in container %s", container.getName());
            return null;
        }
        LOG.debugf("jolokaiContainerPorts determined the container Port to be %s", containerPort);

        return containerPort.getContainerPort();
    }

    private List<Container> containers(Pod pod) throws Exception {
        if (pod == null) {
            return Collections.emptyList();
        }

        if (!this.hasJolokiaPort(pod)) {
            return Collections.emptyList();
        }

        List<Container> containers = pod.getSpec() != null ? pod.getSpec().getContainers() : Collections.emptyList();

        return containers.stream().filter(container -> this.jolokiaContainerPort(container) != null)
                .collect(Collectors.toList());
    }

    private String annotation(Pod pod, String name, String defaultValue) {
        String value = pod.getMetadata().getAnnotations().get(name);
        if (value == null) {
            return defaultValue;
        }

        return value;
    }

    @Override
    public URL connectionURL(Pod pod) throws Exception {
        if (pod == null) {
            throw new Exception("Cannot derive connection url as pod is null");
        }

        if (!hasJolokiaPort(pod)) {
            return null;
        }

        String ip = pod.getStatus().getPodIP();
        String protocol = this.annotation(pod, "hawt.io/protocol", DEFAULT_JOLOKIA_PROTOCOL);
        String jolokiaPathSegment = this.annotation(pod, "hawt.io/jolokiaPath", DEFAULT_JOLOKIA_PATH);
        List<Container> containers = this.containers(pod);

        URL jolokiaURL = null;
        for (Container container : containers) {
            Integer jolokiaPort = this.jolokiaContainerPort(container);
            if (jolokiaPort == null) {
                // Should not happen since these are jolokia containers
                LOG.infof("Could not find a jolokia port in container %s from pod %s", container.getName(),
                        pod.getFullResourceName());
                continue;
            }

            try {
                // https://10.1.1.2/8778/jolokia/
                jolokiaURL = new URI(protocol, null, ip, jolokiaPort, jolokiaPathSegment, null, null).toURL();
            } catch (Exception e) {
                throw new Exception(String.format(
                        "Pod (%s) container (%s) is a jolokia application but failed to extract its jolokia url",
                        pod.getFullResourceName(), container.getName()), e);
            }
        }

        return jolokiaURL;
    }
}
