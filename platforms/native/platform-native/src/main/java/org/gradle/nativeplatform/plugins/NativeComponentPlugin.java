/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin;

import javax.inject.Inject;

/**
 * A plugin that creates tasks used for constructing native binaries.
 */
@Incubating
public abstract class NativeComponentPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        NativeToolChainRegistryInternal toolChains = (NativeToolChainRegistryInternal) getToolChains();
        project.getExtensions().add(NativeToolChainRegistry.class, "toolChains", toolChains);

        project.getPluginManager().apply(StandardToolChainsPlugin.class);

        project.afterEvaluate(p -> {
            // add defaults
            if (toolChains.isEmpty()) {
                toolChains.addDefaultToolChains();
            }
        });
    }

    @Inject
    protected abstract NativeToolChainRegistry getToolChains();

}
