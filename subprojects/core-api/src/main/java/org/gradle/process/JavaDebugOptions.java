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

/**
 * Contains a subset of the <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/conninv.html">Java Debug Wire Protocol</a> properties.
 *
 * @since 5.6
 */
@Incubating
public interface JavaDebugOptions {

    /**
     * Whether to attach a debug agent to the forked process.
     */
    @Input Property<Boolean> getEnabled();

    /**
     * The debug port.
     */
    @Input Property<Integer> getPort();

    /**
     * Whether a socket-attach or a socket-listen type of debugger is expected.
     * <p>
     * In socked-attach mode (server = true) the process actively waits for the debugger to connect after the JVM
     * starts up. In socket-listen mode (server = false), the debugger should be already running before startup
     * waiting for the JVM connecting to it.
     *
     * @return Whether the process actively waits for the debugger to be attached.
     */
    @Input Property<Boolean> getServer();

    /**
     * Whether the forked process should be suspended until the connection to the debugger is established.
     */
    @Input Property<Boolean> getSuspend();
}
