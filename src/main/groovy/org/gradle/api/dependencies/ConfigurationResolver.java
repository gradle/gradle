/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.core.report.ResolveReport;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public interface ConfigurationResolver extends Configuration, FileCollection {
    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * the resulting list of files.
     *
     * @return The files of this configuration.
     */
    List<File> resolve();

    List<File> resolve(ResolveInstructionModifier resolveInstructionModifier);

    List<File> resolve(Closure resolveInstructionConfigureClosure);

    ResolveReport resolveAsReport(ResolveInstructionModifier resolveInstructionModifier);

    ResolveReport resolveAsReport(Closure resolveInstructionConfigureClosure);

    String getUploadInternalTaskName();

    String getUploadTaskName();

    /**
     * Returns a task dependencies object containing all required dependencies to build the project dependencies
     * belonging to this configuration or to one of its super configurations.
     * 
     * @return a task dependency object
     */
    TaskDependency getBuildProjectDependencies();

    /**
     * Returns a task dependencies object containing all required dependencies to build the artifacts
     * belonging to this configuration or to one of its super configurations.
     *
     * @return a task dependency object
     */
    TaskDependency getBuildArtifactDependencies();

    void publish(ResolverContainer publishResolvers, PublishInstruction publishInstruction);

    List<Dependency> getDependencies();

    List<Dependency> getAllDependencies();

    List<ProjectDependency> getProjectDependencies();

    List<ProjectDependency> getAllProjectDependencies();

    Set<PublishArtifact> getArtifacts();

    Set<PublishArtifact> getAllArtifacts();
}
