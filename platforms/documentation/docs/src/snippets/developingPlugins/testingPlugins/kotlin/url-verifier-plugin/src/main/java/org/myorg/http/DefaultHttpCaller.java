package org.myorg.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class DefaultHttpCaller implements HttpCaller {
    @Override
    public HttpResponse get(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            connection.connect();

            int code = connection.getResponseCode();
            String message = connection.getResponseMessage();
            return new HttpResponse(code, message);
        } catch (IOException e) {
            throw new HttpCallException(String.format("Failed to call URL '%s' via HTTP GET", url), e);
        }
    }
}
