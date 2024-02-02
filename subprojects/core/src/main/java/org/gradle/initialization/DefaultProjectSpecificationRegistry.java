/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.initialization.dsl.ProjectSpecification;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DefaultProjectSpecificationRegistry implements ProjectSpecificationRegistry {
    private final Map<String, ProjectSpecification> byPath = new HashMap<>();

    @Override
    public void registerProjectSpecification(ProjectSpecification projectSpecification) {
        byPath.put(projectSpecification.getLogicalPath(), projectSpecification);
    }

    @Override
    public Optional<ProjectSpecification> getProjectSpecificationByPath(String logicalPath) {
        return Optional.ofNullable(byPath.get(logicalPath));
    }

    @Override
    public Optional<ProjectSpecification> getProjectSpecificationByDir(File projectDir) {
        return byPath.values().stream().filter(projectSpecification -> projectDir.equals(projectSpecification.getProjectDir().get().getAsFile())).findFirst();
    }
}
