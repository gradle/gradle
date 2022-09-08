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
import org.gradle.api.toolchain.management.ToolchainManagementSpec;
import org.gradle.jvm.toolchain.JdksBlockForToolchainManagement;
import org.gradle.jvm.toolchain.internal.DefaultJdksBlockForToolchainManagement;

import javax.inject.Inject;

/**
 * Base plugin that needs to be applied if Java toolchain auto-provisioning is needed in the build.
 * <p>
 * Add a dynamic extension to <code>ToolchainManagementSpec</code>, a <code>jdks</code> block with methods
 * to specify which <code>JavaToolchainRepository</code> implementations to use and in what order
 * for Java toolchain provisioning. These repositories are in turn provided by Toolchain Provisioning SPI plugins.
 *
 * @since 7.6
 */
@Incubating
public abstract class JdkToolchainsPlugin implements Plugin<Settings> {

    //TODO (#21082): find a better name, we already hava a JvmToolchainsPlugin, together they aren't ok; maybe merge it with JvmToolchainsPlugin?
    //TODO (#21082): update name in design specs too

    @Inject
    protected abstract DefaultJdksBlockForToolchainManagement getDefaultJdksBlockForToolchainManagement();

    @Override
    public void apply(Settings settings) {
        ToolchainManagementSpec toolchainManagement = settings.getToolchainManagement();
        toolchainManagement.getExtensions()
                .add(JdksBlockForToolchainManagement.class, "jdks", getDefaultJdksBlockForToolchainManagement());
    }
}
