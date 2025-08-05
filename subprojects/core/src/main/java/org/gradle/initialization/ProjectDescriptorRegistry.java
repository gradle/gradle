/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.api.specs.Spec;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Set;

public interface ProjectDescriptorRegistry {

    void addProject(DefaultProjectDescriptor project);

    @Nullable
    DefaultProjectDescriptor getRootProject();

    @Nullable DefaultProjectDescriptor getProject(String path);

    @Nullable DefaultProjectDescriptor getProject(File projectDir);

    int size();

    Set<DefaultProjectDescriptor> getAllProjects();

    Set<DefaultProjectDescriptor> getAllProjects(String path);

    Set<DefaultProjectDescriptor> getSubProjects(String path);

    Set<DefaultProjectDescriptor> findAll(Spec<? super DefaultProjectDescriptor> constraint);

    void changeDescriptorPath(Path oldPath, Path newPath);

}
