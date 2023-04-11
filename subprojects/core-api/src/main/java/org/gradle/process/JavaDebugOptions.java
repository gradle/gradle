/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Contains a subset of the <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/conninv.html">Java Debug Wire Protocol</a> properties.
 *
 * @since 5.6
 */
public interface JavaDebugOptions {

    /**
     * Should the debug agent start in the forked process? By default, this is false.
     */
    @Input
    Property<Boolean> getEnabled();

    /**
     * Host address to listen on or connect to when debug is enabled. By default, no host is set.
     *
     * <p>
     * When run in {@link #getServer() server} mode, the process listens on the loopback address on Java 9+ and all interfaces on Java 8 and below by default.
     * Setting host to {@code *} will make the process listen on all network interfaces. This is not supported on Java 8 and below.
     * Setting host to anything else will make the process listen on that address.
     * </p>
     * <p>
     * When run in {@link #getServer() client} mode, the process attempts to connect to the given host and {@link #getPort()}.
     * </p>
     *
     * @since 7.6
     */
    @Incubating
    @Optional
    @Input
    Property<String> getHost();

    /**
     * The debug port to listen on or connect to.
     */
    @Input
    Property<Integer> getPort();

    /**
     * Should the process listen for a debugger to attach (server) or immediately connect to an already running debugger (client)?
     * <p>
     * In server mode ({@code server = true}), the process listens for a debugger to connect after the JVM starts up.
     * </p>
     * <p>
     * In client mode ({@code server = false}), the process attempts to connect to an already running debugger.
     * </p>
     */
    @Input
    Property<Boolean> getServer();

    /**
     * Should the process suspend until the connection to the debugger is established?
     */
    @Input
    Property<Boolean> getSuspend();
}
