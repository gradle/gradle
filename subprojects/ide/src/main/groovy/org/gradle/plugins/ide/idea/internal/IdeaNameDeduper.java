/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.internal;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.transform.CompileStatic;
import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.internal.configurer.DeduplicationTarget;
import org.gradle.plugins.ide.internal.configurer.ProjectDeduper;

import java.util.Set;

@CompileStatic
public class IdeaNameDeduper {
    public void configureRoot(Project rootProject) {
        Set<Project> ideaProjects = Sets.filter(rootProject.getAllprojects(), new Predicate<Project>() {
            @Override
            public boolean apply(Project project) {
                return project.getPlugins().hasPlugin(IdeaPlugin.class);
            }

        });
        new ProjectDeduper().dedupe(ideaProjects, new Closure<DeduplicationTarget>(this, this) {
            public DeduplicationTarget doCall(final Project project) {
                DeduplicationTarget target = new DeduplicationTarget();
                final IdeaModule module = ((GenerateIdeaModule) project.getTasks().getByName("ideaModule")).getModule();

                target.setProject(project);
                target.setModuleName(module.getName());
                target.setUpdateModuleName(new Closure<String>(IdeaNameDeduper.this, IdeaNameDeduper.this) {
                    public String doCall(String moduleName) {
                        module.setName(moduleName);
                        return moduleName;
                    }

                });
                return target;
            }

        });
    }

}
