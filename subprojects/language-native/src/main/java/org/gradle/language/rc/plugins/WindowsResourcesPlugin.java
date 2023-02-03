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

package org.gradle.language.rc.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from Windows Resource sources.
 *
 * <p>Automatically includes the {@link WindowsResourceScriptPlugin} for core Windows Resource source support and the {@link NativeComponentPlugin} for native component support.</p>
 *
 * <ul>
 * <li>Creates a {@link org.gradle.language.rc.tasks.WindowsResourceCompile} task for each {@link org.gradle.language.rc.WindowsResourceSet} to compile the sources.</li>
 * </ul>
 */
@Incubating
public abstract class WindowsResourcesPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        project.getPluginManager().apply(WindowsResourceScriptPlugin.class);
    }

}
