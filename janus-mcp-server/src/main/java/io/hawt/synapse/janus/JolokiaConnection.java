package io.hawt.synapse.janus;

import java.net.URL;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.fabric8.kubernetes.api.model.Pod;

public interface JolokiaConnection {

    String DEFAULT_JOLOKIA_PROTOCOL = "https";
    String DEFAULT_JOLOKIA_PATH = "/jolokia/";

    Logger LOG = Logger.getLogger(JolokiaConnection.class);

    /**
     * Checks if the given pod's specification contains a container port named
     * 'jolokia'.
     *
     * @param pod The Kubernetes Pod object from the Fabric8 client.
     * @return true if a 'jolokia' port is found, false otherwise.
     * @throws JsonProcessingException
     */
    boolean hasJolokiaPort(Pod pod) throws JsonProcessingException;

    /**
     * 
     * Derives the connection URL of the jolokia port from the application pod
     * 
     * @param pod the pod from which to derive the jolokia url
     * @return the #URL to the jolokia port of the given pod
     * @throws Exception
     */
    public URL connectionURL(Pod pod) throws Exception;
}
