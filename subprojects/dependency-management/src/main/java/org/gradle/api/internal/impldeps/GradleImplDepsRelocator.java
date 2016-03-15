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

package org.gradle.api.internal.impldeps;

import org.objectweb.asm.commons.Remapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GradleImplDepsRelocator extends Remapper {

    private final Pattern classPattern = Pattern.compile("(\\[*)?L(.+)");

    public String map(String name) {
        String value = name;

        String prefix = "";

        Matcher m = classPattern.matcher(name);
        if (m.matches()) {
            prefix = m.group(1) + "L";
            name = m.group(2);
        }

        String relocated = relocateClass(name);
        if (relocated == null) {
            return value;
        } else {
            return prefix.concat(relocated);
        }
    }

    public String relocateClass(String clazz) {
        if (
            clazz.startsWith("org/gradle")
                || clazz.startsWith("java")
                || clazz.startsWith("javax")
                || clazz.startsWith("groovy")
                || clazz.startsWith("net/rubygrapefruit")
                || clazz.startsWith("org/codehaus/groovy")
                || clazz.startsWith("org/apache/tools/ant")
            ) {
            return null;
        } else {
            return "org/gradle/impldep/".concat(clazz);
        }
    }

}