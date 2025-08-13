package io.hawt.synapse.janus;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main {

    public static void main(String... args) {
        // Just run the Quarkus application. The MCP extension will handle the rest.
        Quarkus.run(args);
    }
}
