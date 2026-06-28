/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.server.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Authenticates gRPC calls against the daemon's existing 16-byte authentication token, mirroring
 * the token check the Kryo protocol performs in {@code DefaultIncomingConnectionHandler}.
 */
public class TokenAuthInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> TOKEN_KEY = Metadata.Key.of("x-gradle-daemon-token", Metadata.ASCII_STRING_MARSHALLER);

    private final String expectedToken;

    public TokenAuthInterceptor(byte[] token) {
        this.expectedToken = Base64.getEncoder().encodeToString(token);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String provided = headers.get(TOKEN_KEY);
        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid daemon token"), new Metadata());
            return new ServerCall.Listener<ReqT>() {
            };
        }
        return next.startCall(call, headers);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
