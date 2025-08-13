package io.hawt.synapse.janus;

public interface JolokiaServiceFactory {

    /**
     * Creates a new {@link JolokiaService} instance targeted at a specific URL.
     * 
     * @param url The full base URL for the Jolokia endpoint (e.g.,
     *            http://10.1.2.3:8778/jolokia)
     * @return A new, ready-to-use JolokiaService.
     */
    JolokiaService createService(String url);
}
