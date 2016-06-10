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

import org.objectweb.asm.commons.Remapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ImplementationDependencyRelocator extends Remapper {

    private final Pattern classPattern = Pattern.compile("(\\[*)?L(.+)");

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
        if (
            resource.startsWith("META-INF")
                || resource.startsWith("org/gradle")
                || resource.startsWith("java")
                || resource.startsWith("javax")
                || resource.startsWith("groovy")
                || resource.startsWith("groovyjarjarantlr")
                || resource.startsWith("net/rubygrapefruit")
                || resource.startsWith("org/codehaus/groovy")
                || resource.startsWith("org/apache/tools/ant")
                || resource.startsWith("org/apache/commons/logging")
                || resource.startsWith("org/slf4j")
                || resource.startsWith("org/apache/log4j")
                || resource.startsWith("org/apache/xerces")
                || resource.startsWith("org/cyberneko/html")
                || resource.startsWith("org/w3c/dom")
                || resource.startsWith("org/xml/sax")
            ) {
            return null;
        } else {
            return "org/gradle/internal/impldep/" + resource;
        }
    }

    public boolean keepOriginalResource(String resource) {
        return resource == null || maybeRelocateResource(resource) == null
            || !resource.startsWith("com/sun/jna"); // in order to use a newer version of jna the resources must not be available in the old location
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
