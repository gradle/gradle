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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.buildconfiguration.tasks.UpdateDaemonJvmModifier;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.util.internal.IncubationLogger;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

        final String jvmVendor;
        if (getJvmVendor().isPresent()) {
            jvmVendor = getJvmVendor().get();
        } else {
            jvmVendor = null; // any vendor is acceptable
        }
        UpdateDaemonJvmModifier.updateJvmCriteria(
            getPropertiesFile().get().getAsFile(),
            getJvmVersion().get(),
            jvmVendor,
            null
        );
    }

    /**
     * The file to write the requested daemon JVM criteria to.
     *
     * {@value DaemonJvmPropertiesDefaults#DAEMON_JVM_PROPERTIES_FILE}
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
    public abstract Property<JavaLanguageVersion> getJvmVersion();

    /**
     * The vendor of Java required to run the Gradle Daemon.
     * <p>
     * When unset, any vendor is acceptable.
     * </p>
     *
     * @since 8.10
     */
    @Input
    @Optional
    @Incubating
    @Option(option = "jvm-vendor", description = "The vendor of the JVM required to run the Gradle Daemon.")
    public abstract Property<String> getJvmVendor();

    /**
     * Returns the supported JVM vendors.
     *
     * @return supported JVM vendors
     * @since 8.10
     */
    @OptionValues("jvm-vendor")
    public List<String> getAvailableVendors() {
        return Arrays.stream(JvmVendor.KnownJvmVendor.values()).filter(e -> e!=JvmVendor.KnownJvmVendor.UNKNOWN).map(Enum::name).collect(Collectors.toList());
    }
}
