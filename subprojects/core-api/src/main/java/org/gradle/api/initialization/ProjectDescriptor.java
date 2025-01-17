/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.initialization;

import org.gradle.declarative.dsl.model.annotations.Restricted;
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

/**
 * <p>A {@code ProjectDescriptor} declares the configuration required to create and evaluate a {@link
 * org.gradle.api.Project}.</p>
 *
 * <p> A {@code ProjectDescriptor} is created when you add a project to the build from the settings script, using {@link
 * Settings#include(String...)}. You can access the descriptors using one of
 * the lookup methods on the {@link Settings} object.</p>
 */
@NotToBeMigratedToLazy
public interface ProjectDescriptor {
    /**
     * Returns the name of this project.
     *
     * @return The name of the project. Never returns null.
     */
    @Restricted
    String getName();

    /**
     * Sets the name of this project.
     *
     * @param name The new name for the project. Should not be null
     */
    void setName(String name);

    /**
     * Returns the project directory of this project.
     *
     * @return The project directory. Never returns null.
     */
    File getProjectDir();

    /**
     * Sets the project directory of this project.
     *
     * @param dir The new project directory. Should not be null.
     */
    void setProjectDir(File dir);

    /**
     * Returns the name of the build file for this project. This name is interpreted relative to the project
     * directory.
     *
     * @return The build file name.
     */
    String getBuildFileName();

    /**
     * Sets the name of the build file. This name is interpreted relative to the project directory.
     *
     * @param name The build file name. Should not be null.
     */
    void setBuildFileName(String name);

    /**
     * Returns the build file for this project.
     *
     * @return The build file. Never returns null.
     */
    File getBuildFile();

    /**
     * Returns the parent of this project, if any. Returns null if this project is the root project.
     *
     * @return The parent, or null if this is the root project.
     */
    @Nullable
    ProjectDescriptor getParent();

    /**
     * Returns the children of this project, if any.
     *
     * @return The children. Returns an empty set if this project does not have any children.
     */
    Set<ProjectDescriptor> getChildren();

    /**
     * Returns the path of this project. The path can be used as a unique identifier for this project.
     *
     * @return The path. Never returns null.
     */
    String getPath();
}
