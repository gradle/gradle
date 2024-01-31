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
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.buildconfiguration.UpdateDaemonJvmModifier;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates or updates Daemon JVM criteria.
 */
@DisableCachingByDefault(because = "Not worth caching")
public class UpdateDaemonJvmTask extends DefaultTask {

    public static final String TASK_NAME = "updateDaemonJvm";

    private final UpdateDaemonJvmModifier updateDaemonJvmModifier = new UpdateDaemonJvmModifier(getProject().getProjectDir());
    private final Property<Integer> toolchainVersion = getProject().getObjects().property(Integer.class);
    private JvmVendor toolchainVendor;
    private JvmImplementation toolchainImplementation;

    @TaskAction
    void generate() {
        updateDaemonJvmModifier.updateJvmCriteria(
            toolchainVersion.get(),
            toolchainVendor,
            toolchainImplementation
        );
    }

    @OutputFile
    public File getPropertiesFile() {
        return updateDaemonJvmModifier.getPropertiesFile();
    }

    @Nonnull
    @Input
    @Optional
    @Option(option = "toolchain-version", description = "The version of the toolchain required to set up Daemon JVM")
    public Property<Integer> getToolchainVersion() {
        return toolchainVersion;
    }

    @Input
    @Optional
    public JvmVendor getToolchainVendor() {
        return toolchainVendor;
    }

    @Option(option = "toolchain-vendor", description = "The vendor of the toolchain required to set up Daemon JVM")
    public void setToolchainVendor(KnownJvmVendor vendor) {
        toolchainVendor = vendor.asJvmVendor();
    }

    /**
     * The list of available toolchain vendors.
     */
    @OptionValues("toolchain-vendor")
    public List<JvmVendor> getAvailableToolchainVendors() {
        return Arrays.stream(KnownJvmVendor.values()).map(KnownJvmVendor::asJvmVendor).collect(Collectors.toList());
    }

    @Input
    @Optional
    public JvmImplementation getToolchainImplementation() {
        return toolchainImplementation;
    }

    @Option(option = "toolchain-implementation", description = "The virtual machine implementation of the toolchain required to set up Daemon JVM")
    public void setToolchainImplementation(JvmImplementation implementation) {
        toolchainImplementation = implementation;
    }

    /**
     * The list of available toolchain implementations.
     */
    @OptionValues("toolchain-implementation")
    public List<JvmImplementation> getAvailableToolchainImplementations() {
        return Arrays.asList(JvmImplementation.values());
    }
}
