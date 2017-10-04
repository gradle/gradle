/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.initialization.DefaultProjectDescriptor;

public interface SettingsInternal extends Settings, PluginAwareInternal {
    /**
     * Returns the scope containing classes that should be visible to all settings scripts and build scripts invoked by this build.
     */
    ClassLoaderScope getRootClassLoaderScope();

    /**
     * Returns the scope into which the main settings script should define classes, and from which plugins applied to this settings object should be resolved.
     */
    ClassLoaderScope getClassLoaderScope();

    StartParameter getStartParameter();

    ScriptSource getSettingsScript();

    ProjectRegistry<DefaultProjectDescriptor> getProjectRegistry();

    ProjectDescriptor getDefaultProject();

    void setDefaultProject(ProjectDescriptor defaultProject);

    @Override
    GradleInternal getGradle();
}
