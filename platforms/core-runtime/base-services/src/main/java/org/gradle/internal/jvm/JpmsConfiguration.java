/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.jvm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * These JVM arguments should be passed to any Gradle process that will be running on Java 9+
 * Gradle accesses those packages reflectively. On Java versions 9 to 15, the users will get
 * a warning they can do nothing about. On Java 16+, strong encapsulation of JDK internals is
 * enforced and not having the explicit permissions for reflective accesses will result in runtime exceptions.
 *
 * <p>
 * For the {@code --enable-native-access} flag, users will get a warning on Java 24+ if the flag is not present.
 * There is no enforcement of the flag at the time of writing.
 * </p>
 */
public class JpmsConfiguration {

    /**
     * Exposes the required packages for Groovy to work on Java 9+.
     */
    private static final List<String> GROOVY_JPMS_ARGS_9;

    /**
     * These flags are only needed when processes use native services.
     */
    private static final List<String> GRADLE_SHARED_JPMS_ARGS_24;

    /**
     * Enables native access for worker processes.
     */
    private static final List<String> GRADLE_WORKER_JPMS_ARGS_24;

    /**
     * Exposes the required packages for the Gradle daemon to work on Java 9+.
     */
    private static final List<String> GRADLE_DAEMON_JPMS_ARGS_9;

    /**
     * Exposes the required packages for the Gradle daemon to work on Java 9+.
     * Also enables native access for the daemon process.
     */
    private static final List<String> GRADLE_DAEMON_JPMS_ARGS_24;

    static {
        GROOVY_JPMS_ARGS_9 = Collections.unmodifiableList(Arrays.asList(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED", // required by PreferenceCleaningGroovySystemLoader
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", // Required by JdkTools and JdkJavaCompiler
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" // Required by JdkTools and JdkJavaCompiler
        ));

        GRADLE_SHARED_JPMS_ARGS_24 = Collections.singletonList(
            "--enable-native-access=ALL-UNNAMED" // required by NativeServices to access native libraries
        );

        GRADLE_WORKER_JPMS_ARGS_24 = GRADLE_SHARED_JPMS_ARGS_24;

        List<String> gradleDaemonJvmArgs = new ArrayList<String>(GROOVY_JPMS_ARGS_9);

        List<String> configurationCacheJpmsArgs = Collections.unmodifiableList(Arrays.asList(
            "--add-opens=java.base/java.util=ALL-UNNAMED", // for overriding environment variables
            "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED", // required by JavaObjectSerializationCodec.kt
            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED", // required by BeanSchemaKt
            "--add-opens=java.base/java.net=ALL-UNNAMED", // required by JavaObjectSerializationCodec
            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED", // serialized from org.gradle.internal.file.StatStatistics$Collector
            "--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED" // serialized from IvyDescriptorFileGenerator.Model
        ));
        gradleDaemonJvmArgs.addAll(configurationCacheJpmsArgs);

        GRADLE_DAEMON_JPMS_ARGS_9 = Collections.unmodifiableList(gradleDaemonJvmArgs);

        List<String> gradleDaemonJvmArgs24 = new ArrayList<String>(GRADLE_DAEMON_JPMS_ARGS_9);
        gradleDaemonJvmArgs24.addAll(GRADLE_SHARED_JPMS_ARGS_24);
        GRADLE_DAEMON_JPMS_ARGS_24 = Collections.unmodifiableList(gradleDaemonJvmArgs24);
    }

    public static List<String> forGroovyProcesses(int majorVersion) {
        if (majorVersion < 9) {
            return Collections.emptyList();
        }
        return GROOVY_JPMS_ARGS_9;
    }

    /**
     * Returns the JVM arguments that should be passed to a worker process.
     *
     * @param majorVersion the major version of the JVM for the worker process
     * @param usingNativeServices whether the worker process is using native services, {@code true} if it cannot be determined
     * @return the JVM arguments
     */
    public static List<String> forWorkerProcesses(int majorVersion, boolean usingNativeServices) {
        if (majorVersion < 24 || !usingNativeServices) {
            return Collections.emptyList();
        }
        return GRADLE_WORKER_JPMS_ARGS_24;
    }

    /**
     * Returns the JVM arguments that should be passed to a daemon process.
     *
     * @param majorVersion the major version of the JVM for the daemon process
     * @param usingNativeServices whether the daemon process is using native services, {@code true} if it cannot be determined
     * @return the JVM arguments
     */
    public static List<String> forDaemonProcesses(int majorVersion, boolean usingNativeServices) {
        if (majorVersion < 9) {
            return Collections.emptyList();
        }
        if (majorVersion < 24 || !usingNativeServices) {
            return GRADLE_DAEMON_JPMS_ARGS_9;
        }
        return GRADLE_DAEMON_JPMS_ARGS_24;
    }
}
