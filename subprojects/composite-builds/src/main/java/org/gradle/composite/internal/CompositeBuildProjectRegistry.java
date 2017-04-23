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
package org.gradle.composite.internal;

import com.google.common.collect.Sets;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.initialization.BuildProjectRegistry;

import java.util.Collection;
import java.util.Set;

// TODO:DAZ Scope this correctly so it can be made immutable
public class CompositeBuildProjectRegistry implements BuildProjectRegistry {
    private final Set<ProjectIdentifier> allProjects = Sets.newLinkedHashSet();

    void registerProjects(Collection<? extends ProjectIdentifier> projectIdentifiers) {
        allProjects.addAll(projectIdentifiers);
    }

    @Override
    public Set<ProjectIdentifier> getAllProjects() {
        return allProjects;
    }
}
