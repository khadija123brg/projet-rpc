import java.io.*;
import java.util.*;

public class RPCRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String method;
    private Map<String, Object> parameters;

    public RPCRequest() {
        this.parameters = new HashMap<>();
    }

    public RPCRequest(String method) {
        this.method = method;
        this.parameters = new HashMap<>();
    }

    public RPCRequest(String method, Map<String, Object> parameters) {
        this.method = method;
        this.parameters = parameters;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    @Override
    public String toString() {
        return "RPCRequest{" +
                "method='" + method + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
