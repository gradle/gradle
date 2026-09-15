/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The predefined {@code Transformation} root problem group.
 * <p>
 * Generation or manipulation of code, binaries, or resources before running or packaging.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class TransformationProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected TransformationProblemGroup() {
    }

    /**
     * The {@code Binary Transformation} sub-group.
     * <p>
     * Generation or manipulation of binaries, such as libraries or executables.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getBinaryTransformation();

    /**
     * The {@code Code Transformation} sub-group.
     * <p>
     * Generation or manipulation of code, such as mappers or parsers.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getCodeTransformation();

    /**
     * The {@code Resource Transformation} sub-group.
     * <p>
     * Generation or manipulation of resources, such as texts, images, or documents.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getResourceTransformation();
}
