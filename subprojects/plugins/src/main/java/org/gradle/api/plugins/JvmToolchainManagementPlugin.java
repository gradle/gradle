/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.toolchain.management.ToolchainManagement;
import org.gradle.jvm.toolchain.JvmToolchainManagement;
import org.gradle.jvm.toolchain.internal.DefaultJvmToolchainManagement;

import javax.inject.Inject;

/**
 * A plugin that provides JVM specific {@link ToolchainManagement} configuration.
 *
 * @since 7.6
 */
@Incubating
public abstract class JvmToolchainManagementPlugin implements Plugin<Settings> {

    @Inject
    protected abstract DefaultJvmToolchainManagement getDefaultJvmToolchainManagement();

    @Override
    public void apply(Settings settings) {
        ToolchainManagement toolchainManagement = settings.getToolchainManagement();
        toolchainManagement.getExtensions()
                .add(JvmToolchainManagement.class, "jvm", getDefaultJvmToolchainManagement());
    }
}