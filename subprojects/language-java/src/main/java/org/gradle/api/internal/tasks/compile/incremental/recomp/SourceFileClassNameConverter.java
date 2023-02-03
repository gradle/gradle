/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp;

import java.util.Set;

public interface SourceFileClassNameConverter {
    /**
     * Returns the classes that were compiled from this source file or an empty set if unknown.
     */
    Set<String> getClassNames(String sourceFileRelativePath);

    /**
     * Returns the source files that this class was compiled from.
     * Can be multiple files when the same class declaration was made in several files.
     * This happens e.g. during "copy class" refactorings in IntelliJ.
     * Empty if the source for this class could not be determined.
     */
    Set<String> getRelativeSourcePaths(String className);
}
