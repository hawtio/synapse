package io.hawt.synapse.janus;

public interface TestConstants {

    Integer JOLOKIA_CONTAINER_PORT = 8778;

    String BEARER_TOKEN = "your-mock-bearer-token";

    String NAMESPACE = "hawtio-dev";

    String POD_NAME = "hawtio-example-999";

    String POD_IP = "10.0.0.10";
    
    String MBEAN = "org.apache.camel:context=MyCamel,type=context,name=\"MyCamel\"";

    String MBEAN_ATTRIBUTE = "CamelId";
    
    static String bearerTokenHeader(String token) {
        return "Bearer " + token;
    }

}
