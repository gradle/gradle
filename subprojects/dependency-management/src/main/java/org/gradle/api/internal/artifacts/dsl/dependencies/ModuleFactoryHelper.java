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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;

import javax.annotation.Nullable;

public class ModuleFactoryHelper {
    public static void addExplicitArtifactsIfDefined(ExternalDependency moduleDependency, @Nullable String extension, @Nullable String classifier) {
        String artifactType = extension;
        if (artifactType == null) {
            if (classifier != null) {
                artifactType = DependencyArtifact.DEFAULT_TYPE;
            }
        } else {
            moduleDependency.setTransitive(false);
        }
        if (artifactType != null) {
            moduleDependency.addArtifact(new DefaultDependencyArtifact(moduleDependency.getName(),
                    artifactType, extension, classifier, null));
        }
    }
}
