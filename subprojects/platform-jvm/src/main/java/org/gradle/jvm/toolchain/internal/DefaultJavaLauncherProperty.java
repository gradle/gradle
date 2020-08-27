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
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaLauncherProperty;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;

public class DefaultJavaLauncherProperty extends DefaultProperty<JavaLauncher> implements JavaLauncherProperty {

    private final JavaToolchainQueryService toolchainQueryService;

    @Inject
    public DefaultJavaLauncherProperty(PropertyHost propertyHost, JavaToolchainQueryService toolchainQueryService) {
        super(propertyHost, JavaLauncher.class);
        this.toolchainQueryService = toolchainQueryService;
    }

    @Override
    public void from(Action<? super JavaToolchainSpec> toolchainConfig) {
        set(toolchainQueryService.getToolchainLauncher(toolchainConfig));
    }
}
