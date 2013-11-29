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

package org.gradle.nativebinaries.internal.resolve;

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationParser;
import org.gradle.nativebinaries.Library;
import org.gradle.nativebinaries.NativeLibraryDependency;

class NativeDependencyNotationParser {
    public static NotationParser<Object, NativeLibraryDependency> parser(ProjectFinder projectFinder) {
        return new NotationParserBuilder<NativeLibraryDependency>()
                .resultingType(NativeLibraryDependency.class)
                .parser(new LibraryConverter())
                .parser(new NativeDependencyMapNotationParser(projectFinder))
                .toComposite();
    }

    private static class LibraryConverter extends TypedNotationParser<Library, NativeLibraryDependency> {
        private LibraryConverter() {
            super(Library.class);
        }

        @Override
        protected NativeLibraryDependency parseType(Library notation) {
            return notation.getShared();
        }
    }
}
