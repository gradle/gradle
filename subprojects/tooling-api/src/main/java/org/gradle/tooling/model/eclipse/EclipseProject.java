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
package org.gradle.tooling.model.eclipse;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.ExternalDependency;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HasGradleProject;

import java.util.List;

/**
 * The complete model of an Eclipse project.
 *
 * <p>Note that the names of Eclipse projects are unique, and can be used as an identifier for the project.
 *
 * @since 1.0-milestone-3
 */
public interface EclipseProject extends HierarchicalEclipseProject, HasGradleProject {
    /**
     * {@inheritDoc}
     */
    EclipseProject getParent();

    /**
     * {@inheritDoc}
     */
    DomainObjectSet<? extends EclipseProject> getChildren();

    /**
     * The gradle project that is associated with this project.
     * Typically, a single Eclipse project corresponds to a single gradle project.
     * <p>
     * See {@link HasGradleProject}
     *
     * @return associated gradle project
     * @since 1.0-milestone-5
     */
    GradleProject getGradleProject();

    /**
     * Returns the external dependencies which make up the classpath of this project.
     *
     * @return The dependencies. Returns an empty set if the project has no external dependencies.
     * @since 1.0-milestone-3
     */
    DomainObjectSet<? extends ExternalDependency> getClasspath();

    /**
     * Returns the project natures configured on the project.
     * <p>
     * If the Gradle project applies a plugin which is recognized by 'eclipse' Gradle plugin then the corresponding
     * nature will be automatically part of the result. For example, if the project applies the 'java' plugin the
     * result will contain an {@code "org.eclipse.jdt.core.javanature"} entry.
     * <p>
     * If an older version of Gradle is used then the method returns the default value supplied in the argument.
     *
     * @param defaults The default value to return when the result can't be calculated.
     * @return The list of project natures.
     * @since 2.9
     */
    List<String> getProjectNatures(List<String> defaults);

    /**
     * Returns the project builders configured on the project.
     * <p>
     * If the Gradle project applies a plugin which is recognized by 'eclipse' Gradle plugin then the corresponding
     * builder will be automatically part of the result. For example, if the project applies the 'java' plugin the
     * result will contain a builder instance representing the {@code "org.eclipse.jdt.core.javabuilder"}.
     * <p>
     * If an older version of Gradle is used then the method returns a default value supplied in the argument.
     *
     * @param defaults The default value to return when the result can't be calculated.
     * @return The list of project builders.
     * @since 2.9
     */
    List<? extends BuildCommand> getBuildCommands(List<? extends BuildCommand> defaults);
}
