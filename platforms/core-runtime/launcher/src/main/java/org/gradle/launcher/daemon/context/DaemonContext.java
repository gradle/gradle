/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.context;

import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

import java.io.File;
import java.util.Collection;

/**
 * A value object that describes a daemons environment/context.
 * <p>
 * This is used by clients to determine whether or not a daemon meets its requirements
 * such as JDK version, special system properties etc.
 * <p>
 * Instances are serialized by {@link org.gradle.launcher.daemon.context.DefaultDaemonContext.Serializer}
 * and shared via the Daemon Registry.
 *
 * Implementation is immutable.
 *
 * @see DaemonCompatibilitySpec
 * @see DaemonRequestContext
 */
@ServiceScope(Scope.Global.class)
public interface DaemonContext {

    /**
     * The unique identifier for this daemon.
     */
    String getUid();

    /**
     * The JAVA_HOME in use, as the canonical file.
     */
    File getJavaHome();

    JavaLanguageVersion getJavaVersion();

    /**
     * The directory that should be used for daemon storage (not including the gradle version number).
     */
    File getDaemonRegistryDir();

    /**
     * The process id of the daemon.
     */
    Long getPid();

    /**
     * The daemon's idle timeout in milliseconds.
     */
    Integer getIdleTimeout();

    /**
     * Returns the JVM options that the daemon was started with.
     *
     * @return the JVM options that the daemon was started with
     */
    Collection<String> getDaemonOpts();

    /**
     * Returns whether the instrumentation agent should be applied to the daemon
     *
     * @return {@code true} if the agent should be applied
     */
    boolean shouldApplyInstrumentationAgent();

    /**
     * Returns whether the daemon should use native services.
     */
    NativeServicesMode getNativeServicesMode();

    DaemonParameters.Priority getPriority();

    DaemonRequestContext toRequest();
}
