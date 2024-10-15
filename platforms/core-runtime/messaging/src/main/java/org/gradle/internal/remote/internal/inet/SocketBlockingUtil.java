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

package org.gradle.internal.remote.internal.inet;

import java.io.IOException;
import java.nio.channels.SocketChannel;

class SocketBlockingUtil {
    private SocketBlockingUtil() {
    }

    static void configureNonblocking(SocketChannel socket) throws IOException {
        // NOTE: we use non-blocking IO as there is no reliable way when using blocking IO to shutdown reads while
        // keeping writes active. For example, Socket.shutdownInput() does not work on Windows.
        socket.configureBlocking(false);
    }
}
