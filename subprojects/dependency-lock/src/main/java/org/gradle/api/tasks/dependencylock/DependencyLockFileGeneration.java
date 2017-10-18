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

package org.gradle.api.tasks.dependencylock;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.DependencyVersion;
import org.gradle.internal.dependencylock.model.GroupAndName;
import org.gradle.internal.dependencylock.writer.DependencyLockWriter;
import org.gradle.internal.dependencylock.writer.JsonDependencyLockWriter;
import org.gradle.internal.dependencylock.writer.StandardOutputDependencyLockWriter;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class DependencyLockFileGeneration extends DefaultTask {

    private File lockFile;

    @TaskAction
    public void generate() {
        DependencyLock dependencyLock = determineLocks();

        DependencyLockWriter standardOutputDependencyLockWriter = new StandardOutputDependencyLockWriter();
        standardOutputDependencyLockWriter.write(dependencyLock);
        DependencyLockWriter jsonDependencyLockWriter = new JsonDependencyLockWriter(getLockFile());
        jsonDependencyLockWriter.write(dependencyLock);
    }

    @OutputFile
    public File getLockFile() {
        return lockFile;
    }

    public void setLockFile(File lockFile) {
        this.lockFile = lockFile;
    }

    private DependencyLock determineLocks() {
        final DependencyLock dependencyLock = new DependencyLock();

        getProject().getConfigurations().all(new Action<Configuration>() {
            @Override
            public void execute(final Configuration configuration) {
                if (configuration.isCanBeResolved()) {
                    final String configurationName = configuration.getName();

                    configuration.getAllDependencies().withType(ExternalDependency.class, new Action<ExternalDependency>() {
                        @Override
                        public void execute(ExternalDependency externalDependency) {
                            ModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier(externalDependency.getGroup(), externalDependency.getName(), externalDependency.getVersion());
                            dependencyLock.addDependency(configurationName, moduleVersionIdentifier);
                        }
                    });

                    Set<ResolvedDependency> resolvedDependencies = configuration.getResolvedConfiguration().getFirstLevelModuleDependencies();

                    for (ResolvedDependency resolvedDependency : resolvedDependencies) {
                        Map<GroupAndName, DependencyVersion> deps = dependencyLock.getMapping().get(configurationName);
                        GroupAndName groupAndName = new GroupAndName(resolvedDependency.getModuleGroup(), resolvedDependency.getModuleName());

                        if (deps.containsKey(groupAndName)) {
                            deps.get(groupAndName).setResolvedVersion(resolvedDependency.getModuleVersion());
                        }
                    }
                }
            }
        });

        return dependencyLock;
    }
}
