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

package org.gradle.tooling.internal.provider;

import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.IdeaPlugin;

import java.util.Set;

/**
 * @author: Szczepan Faber, created at: 7/19/11
 */
public class IdeaPluginApplier {
    public void apply(Project root) {
        //TODO SF some duplication with the eclipse
        Set<Project> allprojects = root.getAllprojects();
        for (Project p : allprojects) {
            if (!p.getPlugins().hasPlugin(IdeaPlugin.class)) {
                p.getPlugins().apply(IdeaPlugin.class);
            }
        }
//        root.getPlugins().getPlugin(EclipsePlugin.class).makeSureProjectNamesAreUnique();
    }
}
