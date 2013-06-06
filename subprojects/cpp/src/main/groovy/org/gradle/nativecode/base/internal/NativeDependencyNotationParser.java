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

package org.gradle.nativecode.base.internal;

import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.nativecode.base.Library;
import org.gradle.nativecode.base.LibraryBinary;
import org.gradle.nativecode.base.NativeDependencySet;

public class NativeDependencyNotationParser {
    public static NotationParser<NativeDependencySet> parser() {
        return new NotationParserBuilder<NativeDependencySet>()
                .resultingType(NativeDependencySet.class)
                .parser(new LibraryToNativeDependencyConverter())
                .parser(new LibraryBinaryToNativeDependencyConverter())
                .toComposite();
    }

    private static class LibraryToNativeDependencyConverter extends TypedNotationParser<Library, NativeDependencySet> {
        private LibraryToNativeDependencyConverter() {
            super(Library.class);
        }

        @Override
        protected NativeDependencySet parseType(Library notation) {
            return notation.getShared();
        }
    }

    private static class LibraryBinaryToNativeDependencyConverter extends TypedNotationParser<LibraryBinary, NativeDependencySet> {
        private LibraryBinaryToNativeDependencyConverter() {
            super(LibraryBinary.class);
        }

        @Override
        protected NativeDependencySet parseType(LibraryBinary notation) {
            return notation.getAsNativeDependencySet();
        }
    }
}
