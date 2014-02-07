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
package org.gradle.nativebinaries.platform.internal;

import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationParser;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.nativebinaries.platform.OperatingSystem;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class OperatingSystemNotationParser {

    private static final List<String> WINDOWS_ALIASES = Arrays.asList("windows");
    private static final List<String> OSX_ALIASES = Arrays.asList("osx", "mac os x");
    private static final List<String> LINUX_ALIASES = Arrays.asList("linux");
    private static final List<String> SOLARIS_ALIASES = Arrays.asList("solaris", "sunos");
    private static final List<String> FREEBSD_ALIASES = Arrays.asList("freebsd");

    public static NotationParser<Object, OperatingSystem> parser() {
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
            if (FREEBSD_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, org.gradle.internal.os.OperatingSystem.FREE_BSD);
            }
            throw new UnsupportedNotationException(notation);
        }

        @Override
        public void describe(Collection<String> candidateFormats) {
            List<String> allValues = new ArrayList<String>();
            allValues.addAll(WINDOWS_ALIASES);
            allValues.addAll(OSX_ALIASES);
            allValues.addAll(LINUX_ALIASES);
            allValues.addAll(SOLARIS_ALIASES);
            candidateFormats.add("One of the following values: " + GUtil.toString(allValues));
        }
    }
}
