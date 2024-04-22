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

package org.gradle.buildconfiguration.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults;
import org.gradle.internal.buildconfiguration.tasks.UpdateDaemonJvmModifier;
import org.gradle.util.internal.IncubationLogger;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

/**
 * Generates or updates the Gradle Daemon JVM criteria.
 *
 * This controls the version of the JVM required to run the Gradle Daemon.
 *
 * @since 8.8
 */
@DisableCachingByDefault(because = "Not worth caching")
@Incubating
public abstract class UpdateDaemonJvm extends DefaultTask {
    /**
     * Constructor.
     *
     * @since 8.8
     */
    @Inject
    public UpdateDaemonJvm() {

    }

    @TaskAction
    void generate() {
        IncubationLogger.incubatingFeatureUsed("Daemon JVM criteria");
        UpdateDaemonJvmModifier.updateJvmCriteria(
            getPropertiesFile().get().getAsFile(),
            getJvmVersion().get(),
            null,
            null
        );
    }

    /**
     * The file to write the requested daemon JVM criteria to.
     *
     * {@value BuildPropertiesDefaults#BUILD_PROPERTIES_FILE}
     *
     * @since 8.8
     */
    @OutputFile
    @Incubating
    public abstract RegularFileProperty getPropertiesFile();

    /**
     * The version of the JVM required to run the Gradle Daemon.
     *
     * @since 8.8
     */
    @Input
    @Optional
    @Option(option = "jvm-version", description = "The version of the JVM required to run the Gradle Daemon.")
    @Incubating
    public abstract Property<JavaVersion> getJvmVersion();
}
