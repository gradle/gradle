/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.xdcl.jvm.internal;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.xdcl.ecosystem.XdclEcosystemSupport;
import org.gradle.xdcl.provider.XdclSchemaContributions;

import javax.inject.Inject;

/**
 * The built-in XDCL JVM ecosystem plugin. Applied by id from {@code settings.gradle.xdcl}
 * (a distribution plugin, no resolution), it contributes the JVM schema jars to the build's XDCL
 * registry via {@link XdclEcosystemSupport} — so the {@code javaLibrary} templates become available
 * and complete only in a build that opts in by applying this plugin.
 *
 * <p>Abstract with {@code @Inject} getters: a {@code Plugin<Settings>} is instantiated by the
 * settings-scope injecting instantiator, so it can pull the Global-scoped {@link ModuleRegistry} and
 * the Build-scoped {@link XdclSchemaContributions} directly.
 */
public abstract class XdclJvmEcosystemPlugin implements Plugin<Settings> {

    private static final String PLUGIN_ID = "xdcl-jvm-ecosystem";

    @Inject
    protected abstract ModuleRegistry getModuleRegistry();

    @Inject
    protected abstract XdclSchemaContributions getSchemaContributions();

    @Override
    public void apply(Settings settings) {
        XdclEcosystemSupport.contributeSchemas(
            PLUGIN_ID,
            getClass().getClassLoader(),
            getModuleRegistry(),
            getSchemaContributions()
        );
    }
}
