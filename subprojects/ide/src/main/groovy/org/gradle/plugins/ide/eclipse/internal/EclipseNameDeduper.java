/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.internal;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.transform.CompileStatic;
import org.gradle.api.Project;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject;
import org.gradle.plugins.ide.eclipse.model.EclipseProject;
import org.gradle.plugins.ide.internal.configurer.DeduplicationTarget;
import org.gradle.plugins.ide.internal.configurer.ProjectDeduper;

import java.util.Set;

@CompileStatic
public class EclipseNameDeduper {
    public void configureRoot(Project rootProject) {
        Set<Project> eclipseProjects = Sets.filter(rootProject.getAllprojects(), new Predicate<Project>() {
            @Override
            public boolean apply(Project project) {
                return project.getPlugins().hasPlugin(EclipsePlugin.class);
            }
        });
        new ProjectDeduper().dedupe(eclipseProjects, new Closure<DeduplicationTarget>(this, this) {
            public DeduplicationTarget doCall(final Project project) {
                DeduplicationTarget target = new DeduplicationTarget();

                target.setProject(project);
                final EclipseProject projectModel = ((GenerateEclipseProject) project.getTasks().getByName("eclipseProject")).getProjectModel();
                target.setModuleName(projectModel.getName());
                target.setUpdateModuleName(new Closure<String>(EclipseNameDeduper.this, EclipseNameDeduper.this) {
                    public String doCall(String moduleName) {
                        projectModel.setName(moduleName);
                        return moduleName;
                    }

                });
                return target;
            }

        });
    }

}
