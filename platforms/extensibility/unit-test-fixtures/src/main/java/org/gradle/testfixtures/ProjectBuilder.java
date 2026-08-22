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
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.testfixtures.internal.ProjectBuilderImpl;
import org.jspecify.annotations.Nullable;

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
 * @since 0.9
 */
public class ProjectBuilder {

    private String name = "test";
    private @Nullable File projectDir;
    private @Nullable File gradleUserHomeDir;
    private @Nullable Project parent;
    private final ProjectBuilderImpl impl = new ProjectBuilderImpl();

    /**
     * An instance should only be created via the {@link #builder()}.
     * @since 0.9
     */
    private ProjectBuilder() {}

    /**
     * Creates a project builder.
     *
     * @return The builder
     * @since 0.9
     */
    public static ProjectBuilder builder() {
        return new ProjectBuilder();
    }

    /**
     * Returns the default Gradle user home directory.
     *
     * <p>Use the returned directory with {@link #withGradleUserHomeDir(File)} when a ProjectBuilder project should
     * share the default Gradle dependency cache.</p>
     *
     * @return The default Gradle user home directory
     * @since 9.8.0
     */
    @Incubating
    public static File getDefaultGradleUserHomeDir() {
        return new BuildLayoutParameters().getGradleUserHomeDir();
    }

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return The builder
     * @since 0.9
     */
    public ProjectBuilder withProjectDir(@Nullable File dir) {
        projectDir = dir;
        return this;
    }

    /**
     * Specifies the Gradle user home for the builder. Persistent Gradle caches, including the dependency cache,
     * are stored in this directory. If not set, an empty directory under the project directory will be used.
     *
     * @return The builder
     * @since 3.0
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
     * @since 1.0
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
     * @since 1.0
     */
    public ProjectBuilder withParent(@Nullable Project parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Creates the project.
     *
     * @return The project
     * @since 0.9
     */
    public Project build() {
        if (parent != null) {
            return impl.createChildProject(name, parent, projectDir);
        }
        return impl.createProject(name, projectDir, gradleUserHomeDir);
    }
}
