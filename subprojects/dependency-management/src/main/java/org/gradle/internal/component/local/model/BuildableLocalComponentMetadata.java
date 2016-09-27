/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.Map;
import java.util.Set;

public interface BuildableLocalComponentMetadata {
    void addArtifacts(String configuration, Iterable<? extends PublishArtifact> artifacts);

    /**
     * @param hierarchy Must include name
     * @param attributes the attributes of the configuration. Can be null.
     */
    void addConfiguration(String name, String description, Set<String> extendsFrom, Set<String> hierarchy, boolean visible, boolean transitive, Map<String, String> attributes, TaskDependency buildDependencies);

    void addDependency(LocalOriginDependencyMetadata dependency);

    void addExclude(Exclude exclude);
}
