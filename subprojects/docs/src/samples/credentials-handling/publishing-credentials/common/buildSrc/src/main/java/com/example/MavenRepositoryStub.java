package com.example;

import com.github.tomakehurst.wiremock.WireMockServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

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

    private static final int PORT = randomFreePort();
    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().port(PORT));

    public static void start() {
        SERVER.start();

        SERVER.stubFor(put(anyUrl()).willReturn(unauthorized()));
        SERVER.stubFor(put(anyUrl()).withBasicAuth(USERNAME, PASSWORD).willReturn(ok()));
        SERVER.stubFor(get(anyUrl()).withBasicAuth(USERNAME, PASSWORD).willReturn(notFound()));
    }

    public static void stop() {
        SERVER.stop();
    }

    public static String address() {
        return String.format("http://localhost:%d", PORT);
    }

    private static int randomFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
