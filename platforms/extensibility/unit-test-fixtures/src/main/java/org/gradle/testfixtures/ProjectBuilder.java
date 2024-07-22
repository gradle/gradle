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
package org.gradle.testfixtures;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.testfixtures.internal.ProjectBuilderImpl;
import org.gradle.testfixtures.internal.TestVariables;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.io.File;

/**
 * <p>Creates dummy instances of {@link org.gradle.api.Project} which you can use in testing custom task and plugin
 * implementations.</p>
 *
 * <p>To create a project instance:</p>
 *
 * <ol>
 *
 * <li>Create a {@code ProjectBuilder} instance by calling {@link #builder()}.</li>
 *
 * <li>Optionally, configure the builder.</li>
 *
 * <li>Call {@link #build()} to create the {@code Project} instance.</li>
 *
 * </ol>
 *
 * <p>You can reuse a builder to create multiple {@code Project} instances.</p>
 *
 * <p>The {@code ProjectBuilder} implementation bundled with Gradle 3.0 and 3.1 suffers from a
 * binary compatibility issue exposed by applying plugins compiled with Gradle 2.7 and earlier.
 * Applying those pre-compiled plugins in a ProjectBuilder context will result in a {@link ClassNotFoundException}.</p>
 */
public class ProjectBuilder {

    private File projectDir;
    private File gradleUserHomeDir;
    private String name = "test";
    private Project parent;
    private final ProjectBuilderImpl impl = new ProjectBuilderImpl();
    private final TestVariables testVariables = new TestVariables();

    /**
     * An instance should only be created via the {@link #builder()}.
     */
    private ProjectBuilder() { }

    /**
     * Creates a project builder.
     *
     * @return The builder
     */
    public static ProjectBuilder builder() {
        return new ProjectBuilder();
    }

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return The builder
     */
    public ProjectBuilder withProjectDir(@Nullable File dir) {
        projectDir = dir;
        return this;
    }

    /**
     * Specifies the Gradle user home for the builder. If not set, an empty directory under the project directory
     * will be used.
     *
     * @return The builder
     */
    public ProjectBuilder withGradleUserHomeDir(@Nullable File dir) {
        gradleUserHomeDir = dir;
        return this;
    }

    /**
     * Specifies the name for the project
     *
     * @param name project name
     * @return The builder
     */
    public ProjectBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Specifies the parent project. Use it to create multi-module projects.
     *
     * @param parent parent project
     * @return The builder
     */
    public ProjectBuilder withParent(@Nullable Project parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Adds an environment variable to the scope of the project.
     *
     * This environment variable is accessible from the {@link org.gradle.api.provider.ProviderFactory}.
     *
     * @param name the name of the environment variable
     * @param value the value of the environment variable
     * @return the builder
     * @see org.gradle.api.provider.ProviderFactory#environmentVariable(String)
     * @since 8.10
     */
    @Incubating
    public ProjectBuilder withEnvironmentVariable(@Nonnull String name, @Nonnull String value) {
        testVariables.getEnvironmentVariables().put(name, value);
        return this;
    }

    /**
     * Adds a system property to the scope of the project.
     *
     * This system property is accessible from the {@link org.gradle.api.provider.ProviderFactory}.
     *
     * @param name the name of the system property
     * @param value the value of the system property
     * @return the builder
     * @see org.gradle.api.provider.ProviderFactory#systemProperty(String)
     * @since 8.10
     */
    @Incubating
    public ProjectBuilder withSystemProperty(@Nonnull String name, @Nonnull String value) {
        testVariables.getSystemProperties().put(name, value);
        return this;
    }

    /**
     * Adds a gradle property to the scope of the project.
     *
     * This gradle property is accessible from the {@link org.gradle.api.provider.ProviderFactory}.
     *
     * @param name the name of the gradle property
     * @param value the value of the gradle property
     * @return the builder
     * @see org.gradle.api.provider.ProviderFactory#gradleProperty(String)
     * @since 8.10
     */
    @Incubating
    public ProjectBuilder withGradleProperty(@Nonnull String name, @Nonnull String value) {
        testVariables.getGradleProperties().put(name, value);
        return this;
    }

    /**
     * Creates the project.
     *
     * @return The project
     */
    public Project build() {
        if (parent != null) {
            return impl.createChildProject(name, parent, projectDir);
        }
        return impl.createProject(name, projectDir, gradleUserHomeDir, testVariables);
    }
}
