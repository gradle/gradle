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

import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;

/**
 * @author Hans Dockter
 */
public class ModuleFactoryHelper {
    public static void addClassifierArtifactIfSet(String classifier, ExternalDependency dependency) {
        if (classifier != null) {
            dependency.addArtifact(new DefaultDependencyArtifact(dependency.getName(),
                    DependencyArtifact.DEFAULT_TYPE, DependencyArtifact.DEFAULT_TYPE, classifier, null));
        }
    }
}
