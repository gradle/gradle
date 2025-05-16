/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.jvm.extension

import org.gradle.api.provider.Property

/**
 * An extension intended for configuring the manner in which a project is
 * compiled and tested.
 */
abstract class UnitTestAndCompileExtension {

    /**
     * Set this flag to true if the project compiles against JDK internal classes.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesJdkInternals: Property<Boolean>

    /**
     * Set this flag to true if the project compiles against Java standard library APIs
     * that were introduced after the target JVM bytecode version of the project.
     *
     * This workaround should be used sparingly.
     */
    abstract val usesFutureStdlib: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of an entrypoint to user-executed
     * processes, and therefore should compile to a lower version of Java --
     * in order to ensure comprehensible error messages when executing Gradle
     * on an unsupported JVM version.
     * <p>
     * This runtime target should only be used by the various
     * "-main" modules containing main methods.
     */
    abstract val usedForStartup: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the Gradle Wrapper.
     */
    abstract val usedInWrapper: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of worker process.
     */
    abstract val usedInWorkers: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of a client process, such
     * as the Tooling API client or CLI client.
     */
    abstract val usedInClient: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the Gradle daemon.
     */
    abstract val usedInDaemon: Property<Boolean>

}
