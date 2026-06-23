package org.sample.myapp;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url("https://www.example.com")
            .build();

        try {
            Response response = client.newCall(request).execute();

            System.out.println("Response status code: " + response.code());
            System.out.println("Response body: " + response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
