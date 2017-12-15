/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.testing.junit5.internal;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.inject.Inject;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class JUnitPlatformLauncher implements Runnable {
    private final JUnitPlatformOptions options;
    private final int port;

    @Inject
    public JUnitPlatformLauncher(JUnitPlatformOptions options, int port) {
        this.options = options;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            // JUnit looks in the system classloader, if context classloader isn't set
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

            Set<Path> classpathRoots = options.getClasspathRoots().stream()
                .map(File::toPath)
                .collect(Collectors.toSet());

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClasspathRoots(classpathRoots))
                .build();

            try (
                SocketChannel socket = connectToServer();
                ObjectOutputStream outputStream = new ObjectOutputStream(Channels.newOutputStream(socket))
            ) {
                Launcher launcher = LauncherFactory.create();
                launcher.registerTestExecutionListeners(new JUnitPlatformSerializingListener(outputStream));
                launcher.execute(request);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private SocketChannel connectToServer() {
        try {
            InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            return SocketChannel.open(addr);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
