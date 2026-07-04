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
package org.gradle.launcher.daemon.management;

import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.management.internal.ManagedDaemonsBootstrap;

import java.io.File;
import java.util.function.Consumer;

/**
 * Obtains a {@link ManagedDaemons} for the current Gradle version. This is the bootstrap facade that lets a
 * caller talk to daemons without knowing anything about the registry, the connection, or the wire protocol.
 *
 * <p>There are two ways in:
 * <ul>
 *     <li>{@link #withManagedDaemons(File, Consumer)} / {@link #withManagedDaemons(File, File, Consumer)} build
 *     a self-contained client from scratch (native services, logging, registry access). This is what the
 *     standalone daemon management tool uses; the caller needs nothing but this class and {@link ManagedDaemons}.
 *     <li>{@link #managedDaemonsFor(ServiceRegistry)} adapts an existing daemon message-services registry. This
 *     is for callers that already run inside the Gradle service graph (the CLI and the Tooling API provider).
 * </ul>
 */
public abstract class DaemonManagement {

    private DaemonManagement() {
    }

    /**
     * Builds a self-contained {@link ManagedDaemons} for the default daemon registry of the given Gradle user
     * home (honouring the {@code org.gradle.daemon.registry.base} override), invokes {@code action} with it, and
     * tears everything down afterwards.
     */
    public static void withManagedDaemons(File gradleUserHomeDir, Consumer<? super ManagedDaemons> action) {
        ManagedDaemonsBootstrap.withManagedDaemons(gradleUserHomeDir, ManagedDaemonsBootstrap.defaultRegistryDir(gradleUserHomeDir), action);
    }

    /**
     * Builds a self-contained {@link ManagedDaemons} for an explicit daemon registry directory, invokes
     * {@code action} with it, and tears everything down afterwards. The Gradle user home is still required to
     * initialize native services.
     */
    public static void withManagedDaemons(File gradleUserHomeDir, File daemonRegistryDir, Consumer<? super ManagedDaemons> action) {
        ManagedDaemonsBootstrap.withManagedDaemons(gradleUserHomeDir, daemonRegistryDir, action);
    }

    /**
     * Adapts an existing daemon message-services registry (as produced by the Gradle client bootstrap) into a
     * {@link ManagedDaemons}. The caller owns the lifecycle of the supplied registry.
     */
    public static ManagedDaemons managedDaemonsFor(ServiceRegistry messageServices) {
        return ManagedDaemonsBootstrap.fromMessageServices(messageServices);
    }
}
