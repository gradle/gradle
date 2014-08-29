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
package org.gradle.nativeplatform.platform.internal;

import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.*;
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

    public static NotationParser<Object, OperatingSystemInternal> parser() {
        return NotationParserBuilder
                .toType(OperatingSystemInternal.class)
                .typeDisplayName("an object of type OperatingSystem")
                .fromCharSequence(new Parser())
                .toComposite();
    }

    private static final class Parser implements NotationConverter<String, OperatingSystemInternal> {
        public void convert(String notation, NotationConvertResult<? super OperatingSystemInternal> result) throws TypeConversionException {
            OperatingSystemInternal operatingSystem = parseType(notation);
            if (operatingSystem != null) {
                result.converted(operatingSystem);
            }
        }

        protected OperatingSystemInternal parseType(String notation) {
            if (WINDOWS_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, OperatingSystem.WINDOWS);
            }
            if (OSX_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, OperatingSystem.MAC_OS);
            }
            if (LINUX_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, OperatingSystem.LINUX);
            }
            if (SOLARIS_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, OperatingSystem.SOLARIS);
            }
            if (FREEBSD_ALIASES.contains(notation.toLowerCase())) {
                return new DefaultOperatingSystem(notation, OperatingSystem.FREE_BSD);
            }
            return null;
        }

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
