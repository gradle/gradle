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
import org.gradle.api.Project;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;

import javax.inject.Inject;

/**
 * A plugin that provides JVM toolchains for projects that need to execute Java from local JVM installations or run the tools included in a JDK.
 * The plugin makes {@link JavaToolchainService} available via a project extension.
 *
 * @since 7.6
 */
@Incubating
public abstract class JvmToolchainsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getExtensions().create(JavaToolchainService.class, "javaToolchains", DefaultJavaToolchainService.class, javaToolchainQueryService);
    }

    private final JavaToolchainQueryService javaToolchainQueryService;

    @Inject
    public JvmToolchainsPlugin(JavaToolchainQueryService javaToolchainQueryService) {
        this.javaToolchainQueryService = javaToolchainQueryService;
    }
}
