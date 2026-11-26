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

package org.gradle.testing.jacoco.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link JacocoTaskExtension}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface JacocoTaskExtensionInputs {

    /**
     * Whether the task should generate execution data.
     *
     * @see JacocoTaskExtension#isEnabled()
     * @since 10.0.0
     */
    Property<Boolean> getEnabled();

    /**
     * The file the execution data is written to.
     *
     * @see JacocoTaskExtension#getDestinationFile()
     * @since 10.0.0
     */
    RegularFileProperty getDestinationFile();

    /**
     * Class names that are included in the analysis.
     *
     * @see JacocoTaskExtension#getIncludes()
     * @since 10.0.0
     */
    ListProperty<String> getIncludes();

    /**
     * Class names that are excluded from the analysis.
     *
     * @see JacocoTaskExtension#getExcludes()
     * @since 10.0.0
     */
    ListProperty<String> getExcludes();

    /**
     * Class loader names that are excluded from the analysis.
     *
     * @see JacocoTaskExtension#getExcludeClassLoaders()
     * @since 10.0.0
     */
    ListProperty<String> getExcludeClassLoaders();

    /**
     * Whether classes without source location are instrumented.
     *
     * @see JacocoTaskExtension#isIncludeNoLocationClasses()
     * @since 10.0.0
     */
    Property<Boolean> getIncludeNoLocationClasses();

    /**
     * The identifier of the session written to the execution data.
     *
     * @see JacocoTaskExtension#getSessionId()
     * @since 10.0.0
     */
    Property<String> getSessionId();

    /**
     * Whether the coverage data is dumped at VM shutdown.
     *
     * @see JacocoTaskExtension#isDumpOnExit()
     * @since 10.0.0
     */
    Property<Boolean> getDumpOnExit();

    /**
     * The type of output to generate.
     *
     * @see JacocoTaskExtension#getOutput()
     * @since 10.0.0
     */
    Property<JacocoTaskExtension.Output> getOutput();

    /**
     * The IP address or host name used with a TCP output.
     *
     * @see JacocoTaskExtension#getAddress()
     * @since 10.0.0
     */
    Property<String> getAddress();

    /**
     * The port used with a TCP output.
     *
     * @see JacocoTaskExtension#getPort()
     * @since 10.0.0
     */
    Property<Integer> getPort();

    /**
     * Whether functionality is exposed through JMX.
     *
     * @see JacocoTaskExtension#isJmx()
     * @since 10.0.0
     */
    Property<Boolean> getJmx();
}
