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

package org.gradle.launcher.daemon.protocol;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Asks a daemon to cancel the build it is currently running.
 *
 * <p>This is not {@link Cancel}, which travels down the connection that started the build and identifies
 * the build only by which socket it arrived on. That makes it usable solely by the client running the
 * build. This command carries the daemon's authentication token instead and arrives on a connection of
 * its own, so a process that did not start the build, such as an IDE or a management tool, can cancel it.
 *
 * <p>Deliberately not registered with {@code DaemonMessageSerializer}: unregistered commands travel
 * through the Java serialization fallback, whose tag is a constant, so adding this one leaves the wire
 * encoding of every other message untouched.
 */
@NullMarked
public class CancelBuild extends Command {
    public CancelBuild(UUID identifier, byte[] token) {
        super(identifier, token);
    }
}
