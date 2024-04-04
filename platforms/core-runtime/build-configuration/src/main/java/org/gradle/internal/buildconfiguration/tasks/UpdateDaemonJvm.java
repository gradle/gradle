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

package org.gradle.internal.buildconfiguration.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.buildconfiguration.UpdateDaemonJvmModifier;
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.work.DisableCachingByDefault;

/**
 * Generates or updates Daemon JVM criteria.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class UpdateDaemonJvm extends DefaultTask {
    @TaskAction
    void generate() {
        UpdateDaemonJvmModifier.updateJvmCriteria(
            getPropertiesFile().get().getAsFile(),
            JavaVersion.toVersion(getToolchainVersion().get()),
            getToolchainVendor().isPresent() ? getToolchainVendor().get().asJvmVendor() : null,
            getToolchainImplementation().getOrNull()
        );
    }

    @OutputFile
    public abstract RegularFileProperty getPropertiesFile();

    @Input
    @Optional
    @Option(option = "toolchain-version", description = "The version of the toolchain required to set up Daemon JVM")
    public abstract Property<String> getToolchainVersion();

    @Input
    @Optional
    @Option(option = "toolchain-vendor", description = "The vendor of the toolchain required to set up Daemon JVM")
    public abstract Property<KnownJvmVendor> getToolchainVendor();

    @Input
    @Optional
    @Option(option = "toolchain-implementation", description = "The virtual machine implementation of the toolchain required to set up Daemon JVM")
    public abstract Property<JvmImplementation> getToolchainImplementation();
}
