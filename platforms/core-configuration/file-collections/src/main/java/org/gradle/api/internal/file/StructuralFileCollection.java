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

package org.gradle.api.internal.file;

/**
 * A file collection whose inputs are structurally visible without resolving its contents.
 */
interface StructuralFileCollection {
    /**
     * Returns a path-copied collection whose inputs have been rewritten by the given substitution.
     */
    FileCollectionInternal substitute(FileCollectionSubstitution substitution);
}
