/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.runtimeshaded;

import org.gradle.internal.util.Trie;
import org.gradle.model.internal.asm.AsmConstants;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

class ImplementationDependencyRelocator extends Remapper {

    private final Trie prefixes;

    private static Trie readPrefixes(URL resource) {
        final Trie.Builder builder = new Trie.Builder();
        try (InputStream is = resource.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    builder.addWord(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return builder.build();
    }

    public ImplementationDependencyRelocator(URL resource) {
        super(AsmConstants.ASM_LEVEL);
        prefixes = readPrefixes(resource);
    }

    @Override
    public String map(String name) {
        int classNameStart = classNameStart(name);
        String actualName = classNameStart < 0 ? name : name.substring(classNameStart);
        String relocated = maybeRelocateResource(actualName);
        if (relocated == null) {
            return name;
        }
        if (classNameStart < 0) {
            return relocated;
        }
        return name.substring(0, classNameStart).concat(relocated);
    }

    /**
     * Returns the index of the class name within a JVM type descriptor of the form {@code [*Lsome/class},
     * or {@code -1} if {@code name} is already a plain class name with no descriptor prefix.
     */
    private static int classNameStart(String name) {
        int len = name.length();
        int i = 0;
        while (i < len && name.charAt(i) == '[') {
            i++;
        }
        // Must be followed by 'L' and at least one more character to be a descriptor.
        if (i < len && name.charAt(i) == 'L' && i + 1 < len) {
            return i + 1;
        }
        return -1;
    }

    public @Nullable String maybeRelocateResource(String resource) {
        if (prefixes.find(resource)) {
            return "org/gradle/internal/impldep/" + resource;
        }
        return null;
    }

    public boolean keepOriginalResource(String resource) {
        return resource == null
            || maybeRelocateResource(resource) == null
            || !mustBeRelocated(resource);
    }

    private final List<String> mustRelocateList = Arrays.asList(
        // In order to use a newer version of jna the resources must not be available in the old location
        "com/sun/jna",
        "org/apache/groovy",
        // JGit properties work from their relocated locations and conflict if they are left in place.
        "org/eclipse/jgit");

    private final boolean mustBeRelocated(String resource) {
        for (String mustRelocate : mustRelocateList) {
            if (resource.startsWith(mustRelocate)) {
                return true;
            }
        }
        return false;
    }

    public ClassLiteralRemapping maybeRemap(String literal) {
        if (literal.startsWith("class$")) {
            String className = literal.substring(6).replace('$', '.');
            String replacement = maybeRelocateResource(className.replace('.', '/'));
            if (replacement == null) {
                return null;
            }
            String fieldNameReplacement = "class$" + replacement.replace('/', '$');
            return new ClassLiteralRemapping(className, replacement, fieldNameReplacement);
        }
        return null;
    }

    public static class ClassLiteralRemapping {
        private final String literal;
        private final String literalReplacement;
        private final String fieldNameReplacement;

        public ClassLiteralRemapping(String literal, String literalReplacement, String fieldNameReplacement) {
            this.literal = literal;
            this.literalReplacement = literalReplacement;
            this.fieldNameReplacement = fieldNameReplacement;
        }

        public String getLiteral() {
            return literal;
        }

        public String getLiteralReplacement() {
            return literalReplacement;
        }

        public String getFieldNameReplacement() {
            return fieldNameReplacement;
        }
    }

}
