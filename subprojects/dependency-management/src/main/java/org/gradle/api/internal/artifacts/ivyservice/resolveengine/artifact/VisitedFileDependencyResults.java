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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Transformer;
import org.gradle.api.attributes.HasAttributes;

import java.util.Collection;

/**
 * Collects the file dependencies visited during graph traversal. These should be treated as dependencies, but are currently treated separately as a migration step.
 */
public interface VisitedFileDependencyResults {
    /**
     * Selects the files for the matching variant of each node seen during traversal.
     */
    SelectedFileDependencyResults select(Transformer<HasAttributes, Collection<? extends HasAttributes>> selector);
}
