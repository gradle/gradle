package org.gradle;

// The following types can appear anywhere in the code
// but say nothing about API or implementation usage
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class HttpClientWrapper {

    private final HttpClient client; // private member: implementation details

    // HttpClient is used as a parameter of a public method
    // so "leaks" into the public API of this component
    public HttpClientWrapper(HttpClient client) {
        this.client = client;
    }

    // public methods belongs to your API
    public byte[] doRawGet(String url) {
        GetMethod method = new GetMethod(url);
        try {
            int statusCode = doGet(method);
            return method.getResponseBody();

        } catch (Exception e) {
            ExceptionUtils.rethrow(e); // this dependency is internal only
        } finally {
            method.releaseConnection();
        }
        return null;
    }

    // GetMethod is used in a private method, so doesn't belong to the API
    private int doGet(GetMethod method) throws Exception {
        int statusCode = client.executeMethod(method);
        if (statusCode != HttpStatus.SC_OK) {
            System.err.println("Method failed: " + method.getStatusLine());
        }
        return statusCode;
    }
}
