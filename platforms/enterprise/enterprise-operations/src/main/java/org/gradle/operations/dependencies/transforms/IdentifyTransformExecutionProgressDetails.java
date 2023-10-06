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

package org.gradle.operations.dependencies.transforms;

import org.gradle.operations.dependencies.variants.ComponentIdentifier;
import org.gradle.operations.execution.ExecuteWorkBuildOperationType;

import java.util.Map;

/**
 * Fired each time a transform execution is identified by the execution engine.
 * <p>
 * The resulting invocation may be executed at a later point.
 * Most of the time, the execution happens directly after the identification, either as
 * part of a planned transform step or when resolving an artifact view.
 *
 * @since 8.3
 */
public interface IdentifyTransformExecutionProgressDetails {

    /**
     * The opaque identity of the transform execution.
     * <p>
     * Unique within the current build tree.
     *
     * @see ExecuteWorkBuildOperationType.Details#getIdentity()
     */
    String getIdentity();

    /**
     * The component identifier of the input artifact.
     */
    ComponentIdentifier getComponentId();

    /**
     * The from attributes of the registered transform.
     */
    Map<String, String> getFromAttributes();

    /**
     * The to attributes of the registered transform.
     */
    Map<String, String> getToAttributes();

    /**
     * The file name of the input artifact that is about to be transformed.
     */
    String getArtifactName();

    /**
     * The class of the transform action.
     */
    Class<?> getTransformActionClass();

    /**
     * The combined input hash of the secondary inputs.
     * <p>
     * The secondary inputs are the implementation of the transform action and
     * the combined input hash of the parameters of the transform action.
     */
    byte[] getSecondaryInputValueHashBytes();
}
