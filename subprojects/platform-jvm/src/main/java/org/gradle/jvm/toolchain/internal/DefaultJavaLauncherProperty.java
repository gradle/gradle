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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaLauncherProperty;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;

public class DefaultJavaLauncherProperty implements JavaLauncherProperty {

    private final Property<JavaLauncher> javaLauncher;
    // This field is only used during configuration
    private final transient JavaToolchainQueryService toolchainQueryService;

    @Inject
    public DefaultJavaLauncherProperty(Property<JavaLauncher> javaLauncher, JavaToolchainQueryService toolchainQueryService) {
        this.javaLauncher = javaLauncher;
        this.toolchainQueryService = toolchainQueryService;
    }

    @Override
    public void from(Action<? super JavaToolchainSpec> toolchainConfig) {
        javaLauncher.set(toolchainQueryService.getToolchainLauncher(toolchainConfig));
    }

    @Override
    public Provider<JavaLauncher> getProvider() {
        return javaLauncher;
    }
}
