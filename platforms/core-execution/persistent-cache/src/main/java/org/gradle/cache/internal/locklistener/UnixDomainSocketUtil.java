/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.cache.internal.locklistener;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;

import java.lang.reflect.InvocationTargetException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;

@NonNullApi
class UnixDomainSocketUtil {

    private static final List<StandardProtocolFamily> STANDARD_PROTOCOL_FAMILY = ImmutableList.copyOf(StandardProtocolFamily.values());

    static boolean isUnixDomainSocket(SocketAddress socketAddress) {
        return socketAddress.getClass().getName().equals("java.net.UnixDomainSocketAddress");
    }

    static Path unixDomainSocketPath(SocketAddress address) {
        try {
            // Call unixDomainSocketAddress.getPath()
            return (Path) Class.forName("java.net.UnixDomainSocketAddress")
                .getMethod("getPath")
                .invoke(address);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static SocketAddress unixDomainSocketAddressOf(Path address) {
        try {
            // Call UnixDomainSocketAddress.of(address)
            return (SocketAddress) Class.forName("java.net.UnixDomainSocketAddress")
                .getMethod("of", Path.class)
                .invoke(null, address);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static ServerSocketChannel openUnixServerSocketChannel() {
        try {
            // Call ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            return  (ServerSocketChannel) Class.forName("java.nio.channels.ServerSocketChannel")
                .getMethod("open", ProtocolFamily.class)
                .invoke(null, getUnixProtocolFamily());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static SocketChannel openUnixSocketChannel() {
        try {
            // Call ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            return  (SocketChannel) Class.forName("java.nio.channels.SocketChannel")
                .getMethod("open", ProtocolFamily.class)
                .invoke(null, getUnixProtocolFamily());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static StandardProtocolFamily getUnixProtocolFamily() {
        for (StandardProtocolFamily protocolFamily : STANDARD_PROTOCOL_FAMILY) {
            if (protocolFamily.name().equals("UNIX")) {
                return protocolFamily;
            }
        }
        throw new RuntimeException("UNIX protocol family not found");
    }
}
