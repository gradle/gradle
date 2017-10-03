/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

public interface ProjectRegistry<T extends ProjectIdentifier> {
    void addProject(T project);

    @Nullable T getRootProject();

    @Nullable T getProject(String path);

    @Nullable T getProject(File projectDir);

    Set<T> getAllProjects();

    Set<T> getAllProjects(String path);

    Set<T> getSubProjects(String path);

    Set<T> findAll(Spec<? super T> constraint);
}
