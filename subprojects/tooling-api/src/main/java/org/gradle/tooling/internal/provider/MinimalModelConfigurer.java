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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Spec;
import org.gradle.plugins.ide.eclipse.EclipseClasspath;

import java.util.LinkedHashSet;
import java.util.Set;

public class MinimalModelConfigurer extends EclipsePluginApplier {
    @Override
    public void configure(GradleInternal gradle) {
        super.configure(gradle);
        gradle.getRootProject().allprojects(new Action<Project>() {
            public void execute(Project project) {
                project.getTasks().withType(EclipseClasspath.class).all(new Action<EclipseClasspath>() {
                    public void execute(EclipseClasspath eclipseClasspath) {
                        eclipseClasspath.setPlusConfigurations(projectDependenciesOnly(eclipseClasspath.getPlusConfigurations()));
                        eclipseClasspath.setMinusConfigurations(projectDependenciesOnly(eclipseClasspath.getMinusConfigurations()));
                    }
                });
            }
        });
    }

    private Set<Configuration> projectDependenciesOnly(Set<Configuration> configurations) {
        Set<Configuration> filtered = new LinkedHashSet<Configuration>();
        for (Configuration configuration : configurations) {
            filtered.add(configuration.copyRecursive(new Spec<Dependency>() {
                public boolean isSatisfiedBy(Dependency element) {
                    return element instanceof ProjectDependency;
                }
            }));
        }
        return filtered;
    }
}
