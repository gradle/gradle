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
package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;

import java.io.File;
import java.util.List;

public interface SourceIncludesResolver {
    interface IncludeResolutionResult {
        /**
         * Returns true if the include files could be completely resolved. If false, there were additional include files but they could not be resolved.
         *
         * Note that {@link #getFiles()} may contain some files even if this method returns false. This means that the include was partially resolved.
         */
        boolean isComplete();

        String getInclude();

        List<File> getFiles();

        /**
         * Every file path searched as part of resolution.
         */
        List<File> getCheckedLocations();
    }

    /**
     * Resolves the given include directive to zero or more include files.
     */
    IncludeResolutionResult resolveInclude(File sourceFile, Include include, List<IncludeDirectives> visibleIncludeDirectives);
}
