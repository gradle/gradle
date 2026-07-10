/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.docs.asciidoctor;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates valid Asciidoctor identifiers from strings.
 * Tries to mimic original Asciidoctor behaviour.
 *
 * @see <a href="https://docs.asciidoctor.org/asciidoc/latest/sections/auto-ids/#how-a-section-id-is-computed">How a section ID is computed</a>
 */
public class IdGenerator {

    // Matches invalid ID characters in a section title. (taken from https://www.rubydoc.info/gems/asciidoctor/Asciidoctor)
    private static final Pattern ID_PATTERN = Pattern.compile("<[^>]+>|&(?:[a-z][a-z]+\\d{0,2}|#\\d\\d\\d{0,4}|#x[\\da-f][\\da-f][\\da-f]{0,3});|[^ a-zA-Z0-9_\\-.]+?");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[ _.-]+");

    private static final String PART_SEPARATOR = "-";

    static String generateId(String source) {
        String result = source.toLowerCase(Locale.ROOT);

        // replace invalid characters
        result = ID_PATTERN.matcher(result).replaceAll("");

        // normalize separators
        result = SEPARATOR_PATTERN.matcher(result).replaceAll(PART_SEPARATOR);

        // strip separator from the end
        if (result.endsWith(PART_SEPARATOR)) {
            result = result.substring(0, result.length() - 1);
        }

        // string separator from the start
        if (result.startsWith(PART_SEPARATOR)) {
            result = result.substring(1);
        }
        return result;
    }
}
