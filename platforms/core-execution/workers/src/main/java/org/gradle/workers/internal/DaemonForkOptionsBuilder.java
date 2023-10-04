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

package org.gradle.workers.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.JavaForkOptionsInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DaemonForkOptionsBuilder {
    // This isn't exhaustive because there are more ways that extra files can be provided
    // to the worker through diagnostic options, @files or JAVA_TOOL_OPTIONS.
    private static final List<String> UNRELIABLE_OPTIONS = Arrays.asList(
        // Classpath options
        "-cp", "-classpath", "--class-path",
        // Module related options
        "-p", "--module-path", "--upgrade-module-path", "--patch-module"
    );
    // These options allow you to use : instead of a space to separate the
    // option from the value
    private static final List<String> UNRELIABLE_OPTION_PREFIXES = Arrays.asList(
        // bootclasspath can also end with /a or /p
        "-Xbootclasspath",
        // Defining a java agent
        "-javaagent",
        "-agentpath"
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonForkOptionsBuilder.class);

    private final JavaForkOptionsInternal javaForkOptions;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private KeepAliveMode keepAliveMode = KeepAliveMode.DAEMON;
    private ClassLoaderStructure classLoaderStructure = null;

    public DaemonForkOptionsBuilder(JavaForkOptionsFactory forkOptionsFactory) {
        this.forkOptionsFactory = forkOptionsFactory;
        this.javaForkOptions = forkOptionsFactory.newJavaForkOptions();
    }

    public DaemonForkOptionsBuilder keepAliveMode(KeepAliveMode keepAliveMode) {
        this.keepAliveMode = keepAliveMode;
        return this;
    }

    public DaemonForkOptionsBuilder javaForkOptions(JavaForkOptions javaForkOptions) {
        javaForkOptions.copyTo(this.javaForkOptions);
        return this;
    }

    public DaemonForkOptionsBuilder withClassLoaderStructure(ClassLoaderStructure classLoaderStructure) {
        this.classLoaderStructure = classLoaderStructure;
        return this;
    }

    public DaemonForkOptions build() {
        JavaForkOptionsInternal forkOptions = buildJavaForkOptions();
        if (OperatingSystem.current().isWindows() && keepAliveMode == KeepAliveMode.DAEMON) {
            List<String> jvmArgs = forkOptions.getAllJvmArgs();
            Optional<String> unreliableArgument = findUnreliableArgument(jvmArgs);
            if (unreliableArgument.isPresent()) {
                LOGGER.info("Worker requested to be persistent, but the JVM argument '{}' may make the worker unreliable when reused across multiple builds. Worker will expire at the end of the build session.", unreliableArgument.get());
                return new DaemonForkOptions(forkOptions, KeepAliveMode.SESSION, classLoaderStructure);
            }
        }
        return new DaemonForkOptions(forkOptions, keepAliveMode, classLoaderStructure);
    }

    /**
     * Users can add files that are held open by the worker process. This causes problems on Windows with persistent workers because
     * we cannot delete files that are held by the worker process.
     *
     * @param jvmArgs JVM arguments to check
     * @return Optional that has the value of the JVM argument that is unreliable or empty if no unreliable arguments were found
     */
    @VisibleForTesting
    static Optional<String> findUnreliableArgument(List<String> jvmArgs) {
        for (String jvmArg : jvmArgs) {
            if (jvmArg.startsWith("-")) {
                if (UNRELIABLE_OPTIONS.contains(jvmArg)) {
                    return Optional.of(jvmArg);
                }
                for (String prefix : UNRELIABLE_OPTION_PREFIXES) {
                    if (jvmArg.startsWith(prefix)) {
                        return Optional.of(jvmArg);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private JavaForkOptionsInternal buildJavaForkOptions() {
        return forkOptionsFactory.immutableCopy(javaForkOptions);
    }
}
