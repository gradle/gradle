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

import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.util.Trie;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ImplementationDependencyRelocator extends Remapper {

    private final Pattern classPattern = Pattern.compile("(\\[*)?L(.+)");
    private final Trie prefixes;

    private static Trie readPrefixes(RuntimeShadedJarType type) {
        final Trie.Builder builder = new Trie.Builder();
        IoActions.withResource(ImplementationDependencyRelocator.class.getResourceAsStream(type.getIdentifier() + "-relocated.txt"), new ErroringAction<InputStream>() {
            @Override
            protected void doExecute(InputStream thing) throws Exception {
                BufferedReader reader = new BufferedReader(new InputStreamReader(thing, Charset.forName("UTF-8")));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // TODO:instant-execution - remove kotlin predicate after updating the wrapper
                    if (line.length() > 0 && !line.startsWith("kotlin")) {
                        builder.addWord(line);
                    }
                }
            }
        });
        return builder.build();
    }

    public ImplementationDependencyRelocator(RuntimeShadedJarType type) {
        prefixes = readPrefixes(type);
    }

    @Override
    public String map(String name) {
        String value = name;

        String prefix = "";

        Matcher m = classPattern.matcher(name);
        if (m.matches()) {
            prefix = m.group(1) + "L";
            name = m.group(2);
        }

        String relocated = maybeRelocateResource(name);
        if (relocated == null) {
            return value;
        } else {
            return prefix.concat(relocated);
        }
    }

    public String maybeRelocateResource(String resource) {
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
