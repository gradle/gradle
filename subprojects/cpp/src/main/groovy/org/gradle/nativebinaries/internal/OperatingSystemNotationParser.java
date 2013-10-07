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
package org.gradle.nativebinaries.internal;

import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.UnsupportedNotationException;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.nativebinaries.OperatingSystem;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class OperatingSystemNotationParser {

    private static final List<String> WINDOWS_ALIASES = Arrays.asList("windows");
    private static final List<String> OSX_ALIASES = Arrays.asList("osx", "mac os x", "darwin");
    private static final List<String> LINUX_ALIASES = Arrays.asList("linux");
    private static final List<String> SOLARIS_ALIASES = Arrays.asList("solaris", "sunos");

    public static NotationParser<OperatingSystem> parser() {
        return new NotationParserBuilder<OperatingSystem>()
                .resultingType(OperatingSystem.class)
                .parser(new Parser())
                .toComposite();
    }

    private static final class Parser extends TypedNotationParser<String, OperatingSystem> {
        private Parser() {
            super(String.class);
        }

        @Override
        protected OperatingSystem parseType(String notation) {
            if (WINDOWS_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, org.gradle.internal.os.OperatingSystem.WINDOWS);
            }
            if (OSX_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, org.gradle.internal.os.OperatingSystem.MAC_OS);
            }
            if (LINUX_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, org.gradle.internal.os.OperatingSystem.LINUX);
            }
            if (SOLARIS_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, org.gradle.internal.os.OperatingSystem.SOLARIS);
            }
            throw new UnsupportedNotationException(notation);
        }

        @Override
        public void describe(Collection<String> candidateFormats) {
            candidateFormats.addAll(WINDOWS_ALIASES);
            candidateFormats.addAll(OSX_ALIASES);
            candidateFormats.addAll(LINUX_ALIASES);
            candidateFormats.addAll(SOLARIS_ALIASES);
        }
    }

}
