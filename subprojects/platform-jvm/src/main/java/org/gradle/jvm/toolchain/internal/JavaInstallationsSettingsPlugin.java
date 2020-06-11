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

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.jvm.toolchain.JavaInstallationSpec;

import javax.inject.Inject;

public class JavaInstallationsSettingsPlugin implements Plugin<Settings> {

    private final JavaInstallationSpec spec;
    private final SharedJavaInstallationRegistry registry;

    @Inject
    public JavaInstallationsSettingsPlugin(JavaInstallationSpec spec, SharedJavaInstallationRegistry registry) {
        this.spec = spec;
        this.registry = registry;
    }

    public void apply(Settings settings) {
        settings.getExtensions().add("javaInstallations", spec);
        settings.getGradle().settingsEvaluated(s -> registry.finalizeValue());
    }

}
