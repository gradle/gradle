/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.file.collections;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Groovy extension functions for {@link FileCollection} and {@link ConfigurableFileCollection}.
 */
@SuppressWarnings("unused") // registered as Groovy extension in ExtensionModule
public final class FileCollectionExtensions {
    private FileCollectionExtensions() {}

    /**
     * Creates a stand-in for {@link ConfigurableFileCollection} to be used as a left-hand side operand of {@code +=}.
     * <p>
     * The AST transformer knows the name of this method.
     *
     * @param lhs the configurable file collection
     * @return the stand-in object to call {@code plus} on
     * @see org.gradle.api.internal.groovy.support.CompoundAssignmentTransformer
     */
    public static ConfigurableFileCollectionCompoundAssignmentStandIn forCompoundAssignment(ConfigurableFileCollection lhs) {
        return ((DefaultConfigurableFileCollection) lhs).forCompoundAssignment();
    }
}
