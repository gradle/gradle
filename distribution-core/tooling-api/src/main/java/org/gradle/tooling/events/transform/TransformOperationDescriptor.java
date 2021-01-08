/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.events.transform;

import org.gradle.tooling.events.OperationDescriptor;

import java.util.Set;

/**
 * Describes a transform operation for which an event has occurred.
 *
 * @since 5.1
 */
public interface TransformOperationDescriptor extends OperationDescriptor {

    /**
     * Returns the display name of this transform operation.
     */
    TransformerDescriptor getTransformer();

    /**
     * Returns the subject of this transform operation.
     */
    SubjectDescriptor getSubject();

    /**
     * Returns the dependencies (other transforms and tasks) of this transform operation.
     */
    Set<? extends OperationDescriptor> getDependencies();

    /**
     * Describes the transformer of a transform operation.
     *
     * @since 5.1
     */
    interface TransformerDescriptor {

        /**
         * Returns the display name of this transformer.
         */
        String getDisplayName();

    }

    /**
     * Describes the subject (artifact or file) of a transform operation.
     *
     * @since 5.1
     */
    interface SubjectDescriptor {

        /**
         * Returns the display name of this subject.
         */
        String getDisplayName();

    }

}
