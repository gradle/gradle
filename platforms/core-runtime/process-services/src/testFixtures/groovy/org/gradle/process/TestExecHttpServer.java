/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public class TestExecHttpServer {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least one argument");
        }

        System.out.println("Starting server with server info file: " + args[0]);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/test", new TestHandler());
        server.start();
        System.out.println("Server started on port " + server.getAddress().getPort());
        Files.write(new File(args[0]).toPath(), String.valueOf(server.getAddress().getPort()).getBytes());
    }

    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Received request: " + t.getRequestURI());
            String response = "OK";
            t.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    public static String getClassLocation() {
        try {
            return new File(TestExecHttpServer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
