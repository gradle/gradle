/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.objectivec.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from Objective-C sources.
 *
 * <p>Automatically includes the {@link ObjectiveCLangPlugin} for core Objective-C support and the {@link org.gradle.nativeplatform.plugins.NativeComponentPlugin} for native component support.</p>
 *
 * <ul>
 * <li>Creates a {@link org.gradle.language.objectivec.tasks.ObjectiveCCompile} task for each {@link org.gradle.language.objectivec.ObjectiveCSourceSet} to compile the Objective-C sources.</li>
 * </ul>
 */
@Incubating
public abstract class ObjectiveCPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        project.getPluginManager().apply(ObjectiveCLangPlugin.class);
    }

}
