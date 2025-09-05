package io.hawt.synapse.janus;

import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class);

    public static void main(String... args) {
        if (LOG.isDebugEnabled()) {
            Map<String, String> env = System.getenv();
            for (String envName : env.keySet()) {
                LOG.debugf("%s = %s%n", envName, env.get(envName));
            }
        }

        // Just run the Quarkus application. The MCP extension will handle the rest.
        Quarkus.run(args);
    }
}
