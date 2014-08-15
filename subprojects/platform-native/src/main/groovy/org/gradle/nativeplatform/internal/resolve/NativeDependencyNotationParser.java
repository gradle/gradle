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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.tasks.Optional;
import org.gradle.internal.typeconversion.*;
import org.gradle.nativeplatform.NativeLibraryRequirement;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.internal.ProjectNativeLibraryRequirement;

import java.util.Collection;

class NativeDependencyNotationParser {
    public static NotationParser<Object, NativeLibraryRequirement> parser() {
        return NotationParserBuilder
                .toType(NativeLibraryRequirement.class)
                .parser(new LibraryConverter())
                .parser(new NativeLibraryRequirementMapNotationParser())
                .toComposite();
    }

    private static class LibraryConverter extends TypedNotationParser<NativeLibrarySpec, NativeLibraryRequirement> {
        private LibraryConverter() {
            super(NativeLibrarySpec.class);
        }

        @Override
        protected NativeLibraryRequirement parseType(NativeLibrarySpec notation) {
            return notation.getShared();
        }
    }

    private static class NativeLibraryRequirementMapNotationParser extends MapNotationParser<NativeLibraryRequirement> {

        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Map with mandatory 'library' and optional 'project' and 'linkage' keys, e.g. [project: ':someProj', library: 'mylib', linkage: 'static']");
        }

        @SuppressWarnings("unused")
        protected NativeLibraryRequirement parseMap(@MapKey("library") String libraryName, @Optional @MapKey("project") String projectPath, @Optional @MapKey("linkage") String linkage) {
            return new ProjectNativeLibraryRequirement(projectPath, libraryName, linkage);
        }
    }

}
