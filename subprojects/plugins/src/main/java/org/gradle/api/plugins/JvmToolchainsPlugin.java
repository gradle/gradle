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

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.toolchain.management.ToolchainManagement;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmToolchainManagement;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService;
import org.gradle.jvm.toolchain.internal.DefaultJvmToolchainManagement;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;

import javax.inject.Inject;

/**
 * A plugin that provides JVM toolchains for projects that need to execute Java from local JVM installations or run the tools included in a JDK.
 * The plugin makes {@link JavaToolchainService} available via a project extension.
 *
 * @since 7.6
 */
@Incubating
public abstract class JvmToolchainsPlugin implements Plugin<Object> {

    @Inject
    protected abstract JavaToolchainQueryService getJavaToolchainQueryService();

    @Inject
    protected abstract DefaultJvmToolchainManagement getDefaultJvmToolchainManagement();

    //TODO (#21082): update design docs about piggy-backing the old plugin

    @Override
    public void apply(Object target) {
        if (target instanceof Project) {
            Project project = (Project) target;
            project.getExtensions().create(JavaToolchainService.class, "javaToolchains", DefaultJavaToolchainService.class, getJavaToolchainQueryService());
        } else if (target instanceof Settings) {
            Settings settings = (Settings) target;
            ToolchainManagement toolchainManagement = settings.getToolchainManagement();
            toolchainManagement.getExtensions()
                    .add(JvmToolchainManagement.class, "jvm", getDefaultJvmToolchainManagement());
        } else {
            throw new GradleException(JvmToolchainsPlugin.class.getSimpleName() + " can only be used as either a project- or settings-plugin");
        }
    }
}
