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

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.initialization.DefaultToolchainManagementSpec;
import org.gradle.jvm.toolchain.JdksBlockForToolchainManagement;
import org.gradle.jvm.toolchain.internal.DefaultJdksBlockForToolchainManagement;

import javax.inject.Inject;

public abstract class JdkToolchainsPlugin implements Plugin<Settings> {

    @Inject
    protected abstract DefaultJdksBlockForToolchainManagement getDefaultJdksBlockForToolchainManagement();

    @Override
    public void apply(Settings settings) {
        DefaultToolchainManagementSpec toolchainManagement = (DefaultToolchainManagementSpec) settings.getToolchainManagement();
        toolchainManagement.getExtensions()
                .add(JdksBlockForToolchainManagement.class, "jdks", getDefaultJdksBlockForToolchainManagement());
    }
}
