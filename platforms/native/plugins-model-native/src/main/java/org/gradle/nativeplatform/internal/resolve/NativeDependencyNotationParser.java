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

import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationConverter;
import org.gradle.nativeplatform.internal.ProjectNativeLibraryRequirement;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("deprecation")
class NativeDependencyNotationParser {
    public static NotationParser<Object, org.gradle.nativeplatform.NativeLibraryRequirement> parser() {
        return NotationParserBuilder
                .toType(org.gradle.nativeplatform.NativeLibraryRequirement.class)
                .converter(new LibraryConverter())
                .converter(new NativeLibraryRequirementMapNotationConverter())
                .toComposite();
    }

    private static class LibraryConverter extends TypedNotationConverter<org.gradle.nativeplatform.NativeLibrarySpec, org.gradle.nativeplatform.NativeLibraryRequirement> {
        private LibraryConverter() {
            super(org.gradle.nativeplatform.NativeLibrarySpec.class);
        }

        @Override
        protected org.gradle.nativeplatform.NativeLibraryRequirement parseType(org.gradle.nativeplatform.NativeLibrarySpec notation) {
            return notation.getShared();
        }
    }

    private static class NativeLibraryRequirementMapNotationConverter extends MapNotationConverter<org.gradle.nativeplatform.NativeLibraryRequirement> {

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Map with mandatory 'library' and optional 'project' and 'linkage' keys").example("[project: ':someProj', library: 'mylib', linkage: 'static']");
        }

        @SuppressWarnings("unused")
        protected org.gradle.nativeplatform.NativeLibraryRequirement parseMap(
            @MapKey("library") String libraryName,
            @MapKey("project") @Nullable String projectPath,
            @MapKey("linkage") @Nullable String linkage
        ) {
            return new ProjectNativeLibraryRequirement(projectPath, libraryName, linkage);
        }
    }

}
