/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.ImmutableProject;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

public final class DefaultImmutableProject implements ImmutableProject, Serializable {

    private final String name;
    private final String path;
    private final File projectDirectory;
    private final File rootDirectory;

    public DefaultImmutableProject(String name, String path, File projectDirectory, File rootDirectory) {
        this.name = name;
        this.path = path;
        this.projectDirectory = projectDirectory;
        this.rootDirectory = rootDirectory;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @NotNull
    @Override
    public String getPath() {
        return path;
    }

    @NotNull
    @Override
    public File getProjectDirectory() {
        return projectDirectory;
    }

    @NotNull
    @Override
    public File getRootDirectory() {
        return rootDirectory;
    }

    @Override
    public String toString() {
        return "DefaultImmutableProject{" +
            "name='" + name + '\'' +
            ", path='" + path + '\'' +
            ", projectDirectory=" + projectDirectory +
            ", rootDirectory=" + rootDirectory +
            '}';
    }
}
