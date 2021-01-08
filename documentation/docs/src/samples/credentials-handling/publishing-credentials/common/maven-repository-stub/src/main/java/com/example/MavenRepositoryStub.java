package com.example;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class MavenRepositoryStub {

    // tag::credentials[]
    private static final String USERNAME = "secret-user";
    private static final String PASSWORD = "secret-password";
    // end::credentials[]

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    public static String start() {
        SERVER.start();

        SERVER.stubFor(put(anyUrl()).willReturn(unauthorized()));
        SERVER.stubFor(put(anyUrl()).withBasicAuth(USERNAME, PASSWORD).willReturn(ok()));
        SERVER.stubFor(get(anyUrl()).withBasicAuth(USERNAME, PASSWORD).willReturn(notFound()));

        return String.format("http://127.0.0.1:%d", SERVER.port());
    }

    public static void stop() {
        SERVER.stop();
    }

}
