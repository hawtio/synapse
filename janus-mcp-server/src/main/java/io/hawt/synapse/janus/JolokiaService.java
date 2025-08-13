package io.hawt.synapse.janus;

import java.util.List;
import java.util.Optional;

import org.jolokia.json.JSONObject;

public interface JolokiaService {

    Optional<JSONObject> version() throws Exception;

    List<String> listMBeans() throws Exception;

    JSONObject listOperations(String mbean) throws Exception;

    JSONObject listAttributes(String mbean) throws Exception;

    Optional<Object> read(String mbean, String attr) throws Exception;

    Optional<Object> write(String mbean, String attr, Object value) throws Exception;

    Optional<Object> exec(String mbean, String op, Object... args) throws Exception;
}