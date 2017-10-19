/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.plugins.dependencylock;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.dependencylock.DependencyLockFileGeneration;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.DependencyVersion;
import org.gradle.internal.dependencylock.model.GroupAndName;
import org.gradle.internal.dependencylock.reader.DependencyLockReader;
import org.gradle.internal.dependencylock.reader.JsonDependencyLockReader;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class DependencyLockPlugin implements Plugin<Project> {

    public static final String GENERATE_LOCK_FILE_TASK_NAME = "generateDependencyLock";

    @Override
    public void apply(Project project) {
        final File lockFile = new File(project.getProjectDir(), "dependencies.lock");
        createLockFileGenerationTask(project, lockFile);

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                DependencyLockReader dependencyLockReader = new JsonDependencyLockReader(lockFile);
                DependencyLock dependencyLock = dependencyLockReader.read();

                for (Map.Entry<String, LinkedHashMap<GroupAndName, DependencyVersion>> mapping : dependencyLock.getMapping().entrySet()) {
                    final Configuration foundConfiguration = project.getConfigurations().findByName(mapping.getKey());

                    if (foundConfiguration != null) {
                        for (final Map.Entry<GroupAndName, DependencyVersion> dependency : mapping.getValue().entrySet()) {
                            DependencySet declaredDependencies = foundConfiguration.getAllDependencies();
                            DomainObjectSet<ExternalDependency> foundDependencies = declaredDependencies.withType(ExternalDependency.class).matching(new Spec<Dependency>() {
                                @Override
                                public boolean isSatisfiedBy(Dependency element) {
                                    return element.getGroup().equals(dependency.getKey().getGroup()) && element.getName().equals(dependency.getKey().getName());
                                }
                            });
                            foundDependencies.all(new Action<ExternalDependency>() {
                                @Override
                                public void execute(final ExternalDependency externalDependency) {
                                    foundConfiguration.resolutionStrategy(new Action<ResolutionStrategy>() {
                                        @Override
                                        public void execute(ResolutionStrategy resolutionStrategy) {
                                            resolutionStrategy.force(externalDependency.getGroup() + ":" + externalDependency.getName() + ":" + dependency.getValue().getResolvedVersion());
                                        }
                                    });
                                }
                            });
                        }
                    }
                }
            }
        });
    }

    private void createLockFileGenerationTask(Project project, File lockFile) {
        DependencyLockFileGeneration task = project.getTasks().create(GENERATE_LOCK_FILE_TASK_NAME, DependencyLockFileGeneration.class);
        task.setLockFile(lockFile);
    }
}
