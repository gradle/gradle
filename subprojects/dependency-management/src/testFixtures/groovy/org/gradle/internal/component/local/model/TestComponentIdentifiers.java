/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.util.Path;

import java.util.Collections;

public class TestComponentIdentifiers {
    public static ProjectComponentIdentifier newProjectId(String projectPath) {
        return newProjectId(":", projectPath);
    }

    public static ProjectComponentIdentifier newProjectId(String buildPath, String projectPath) {
        Path path = Path.path(projectPath);
        String name = path.getName();
        if (name == null) {
            name = "root";
        }
        return new DefaultProjectComponentIdentifier(new DefaultBuildIdentifier(Path.path(buildPath)), path, path, name);
    }

    public static ProjectComponentSelector newSelector(String projectPath) {
        return newSelector(":", projectPath);
    }

    public static ProjectComponentSelector newSelector(String buildPath, String projectPath) {
        Path path = Path.path(projectPath);
        String name = path.getName();
        if (name == null) {
            name = "root";
        }
        return new DefaultProjectComponentSelector(new DefaultBuildIdentifier(Path.path(buildPath)), path, path, name, ImmutableAttributes.EMPTY, Collections.emptyList());
    }
}
