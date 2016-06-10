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

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.tooling.model.*;

/**
 * The complete model of an Eclipse project.
 *
 * <p>Note that the names of Eclipse projects are unique, and can be used as an identifier for the project.
 *
 * @since 1.0-milestone-3
 */
public interface EclipseProject extends HierarchicalEclipseProject {
    /**
     * {@inheritDoc}
     */
    EclipseProject getParent();

    /**
     * {@inheritDoc}
     */
    DomainObjectSet<? extends EclipseProject> getChildren();

    /**
     * Returns the Java source settings for this project.
     *
     * @return the settings for Java sources or {@code null} if not a Java element.
     * @throws UnsupportedMethodException For Gradle versions older than 2.10, where this method is not supported.
     * @since 2.10
     */
    @Nullable @Incubating
    EclipseJavaSourceSettings getJavaSourceSettings() throws UnsupportedMethodException;

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
    DomainObjectSet<? extends EclipseExternalDependency> getClasspath();

    /**
     * Returns the Eclipse natures configured on the project.
     * <p>
     * Some natures are automatically added to the result based on the Gradle plugins applied on the project.
     * For example, if the project applies the 'java' plugin the result will contain the
     * {@code "org.eclipse.jdt.core.javanature"} entry. Note, that the exact list of automatically added
     * natures is not part of the API and can vary between Gradle releases.
     * <p>
     * The result can be customized via the 'eclipse' plugin configuration.
     *
     * @return The list of Eclipse project natures.
     * @since 2.9
     * @throws UnsupportedMethodException For Gradle versions older than 2.9, where this method is not supported.
     */
    @Incubating
    DomainObjectSet<? extends EclipseProjectNature> getProjectNatures() throws UnsupportedMethodException;

    /**
     * Returns the Eclipse build commands configured on the project.
     * <p>
     * Some build commands are automatically added to the result based on the Gradle plugins applied on the project.
     * For example, if the project applies the 'java' plugin the result will contain the
     * {@code "org.eclipse.jdt.core.javabuilder"} build command. Note, that the exact list of automatically
     * added build commands is not part of the API and can vary between Gradle releases.
     * <p>
     * The result can be customized via the 'eclipse' plugin configuration.
     *
     * @return The list of Eclipse build commands.
     * @since 2.9
     * @throws UnsupportedMethodException For Gradle versions older than 2.9, where this method is not supported.
     */
    @Incubating
    DomainObjectSet<? extends EclipseBuildCommand> getBuildCommands() throws UnsupportedMethodException;

    /**
     * Returns the Eclipse classpath containers defined on the project.
     *
     * @return The list of classpath containers.
     * @since 3.0
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     */
    @Incubating
    DomainObjectSet<? extends EclipseClasspathContainer> getClasspathContainers() throws UnsupportedMethodException;

    /**
     * Returns the output location of this project.
     *
     * @return The project's output location.
     * @since 3.0
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     */
    @Incubating
    EclipseOutputLocation getOutputLocation() throws UnsupportedMethodException;
}
