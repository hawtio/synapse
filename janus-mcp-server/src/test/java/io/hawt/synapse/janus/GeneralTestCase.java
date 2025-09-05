package io.hawt.synapse.janus;

import java.util.HashMap;
import java.util.Map;

public record GeneralTestCase(String name, String bearerToken, String namespace, String podName, String mbeanAttribute, String mbean, String exceptionResult) {

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder implements TestConstants {
        private final String name;
        private String bearerToken = TestConstants.bearerTokenHeader(BEARER_TOKEN);
        private String namespace = NAMESPACE;
        private String podName = POD_NAME;
        private String mbeanAttr = MBEAN_ATTRIBUTE;
        private String mbean = MBEAN;
        private String exceptionResult = new UnsupportedOperationException().getMessage();

        public Builder(String name) {
            this.name = name;
        }

        public Builder withBearerToken(String bearerToken) {
            if (bearerToken == null)
                this.bearerToken = null;
            else {
                this.bearerToken = TestConstants.bearerTokenHeader(bearerToken);
            }

            return this;
        }

        public Builder withNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder withPodName(String podName) {
            this.podName = podName;
            return this;
        }

        public Builder withMbeanAttribute(String attribute, String mbean) {
            this.mbeanAttr = attribute;
            this.mbean = mbean;
            return this;
        }

        public Builder exceptionResult(String exception) {
            this.exceptionResult = exception;
            return this;
        }

        public GeneralTestCase build() {
            return new GeneralTestCase(name, bearerToken, namespace, podName, mbeanAttr, mbean, exceptionResult);
        }
    }
    
    public Map<String, Object> map() {
        Map<String, Object> params = new HashMap<>();
        
        // Conditionally add each property if it's not null
        if (namespace != null) {
            params.put("namespace", namespace);
        }
        if (podName != null) {
            params.put("podName", podName);
        }
        if (mbeanAttribute != null) {
            params.put("attribute", mbeanAttribute);
        }
        if (mbean != null) {
            params.put("mbean", mbean);
        }

        return params;
    }
}
