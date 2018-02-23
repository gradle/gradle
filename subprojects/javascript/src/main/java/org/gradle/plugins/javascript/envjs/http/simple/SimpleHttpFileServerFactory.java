/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.envjs.http.simple;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.plugins.javascript.envjs.http.HttpFileServer;
import org.gradle.plugins.javascript.envjs.http.HttpFileServerFactory;
import org.gradle.plugins.javascript.envjs.http.simple.internal.SimpleFileServerContainer;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.http.resource.FileContext;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

public class SimpleHttpFileServerFactory implements HttpFileServerFactory {

    public HttpFileServer start(File contentRoot, int port) {
        Container container = new SimpleFileServerContainer(new FileContext(contentRoot));

        try {
            final Server server = new ContainerServer(container);
            Connection connection = new SocketConnection(server);
            InetSocketAddress address = new InetSocketAddress(port);
            InetSocketAddress usedAddress = (InetSocketAddress)connection.connect(address);

            return new SimpleHttpFileServer(contentRoot, usedAddress.getPort(), new Stoppable() {
                public void stop() {
                    try {
                        server.stop();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


}
