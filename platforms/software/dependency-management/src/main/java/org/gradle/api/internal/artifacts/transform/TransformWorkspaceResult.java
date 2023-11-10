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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;

import java.io.File;

/**
 * Transform results bound to a workspace.
 */
public interface TransformWorkspaceResult {
    /**
     * Resolves location of the outputs of this result for a given input artifact.
     *
     * Produced outputs don't need to be resolved to locations, since they are absolute paths and can be returned as is.
     * The relative paths of selected parts of the input artifact need to resolved based on the provided input artifact location.
     */
    ImmutableList<File> resolveOutputsForInputArtifact(File inputArtifact);
}
