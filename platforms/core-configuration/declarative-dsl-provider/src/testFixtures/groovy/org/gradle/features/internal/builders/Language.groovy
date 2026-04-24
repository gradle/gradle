/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders

/**
 * The language to generate source code in.
 *
 * <p>Each value carries the per-language syntax fragments shared across the
 * test-fixture builders, so callers route language-dependent rendering through
 * polymorphic methods rather than {@code if (language == KOTLIN)} branches.</p>
 */
enum Language {
    JAVA {
        @Override String propertyAccessor(String objectExpression, String propertyName) {
            return "${objectExpression}.get${JavaSources.capitalize(propertyName)}()"
        }
        @Override String statementEnd() { return ";" }
        @Override String printCall(String expression) { return "System.out.println(${expression});" }
        @Override String asFileExpression() { return ".getAsFile().getAbsolutePath()" }
    },
    KOTLIN {
        @Override String propertyAccessor(String objectExpression, String propertyName) {
            return "${objectExpression}.${propertyName}"
        }
        @Override String statementEnd() { return "" }
        @Override String printCall(String expression) { return "println(${expression})" }
        @Override String asFileExpression() { return ".asFile.absolutePath" }
    }

    /** Returns the source-form accessor for {@code propertyName} on {@code objectExpression}. */
    abstract String propertyAccessor(String objectExpression, String propertyName)

    /** Returns the statement terminator for this language ({@code ";"} for Java, empty for Kotlin). */
    abstract String statementEnd()

    /** Returns a print statement (or expression) that emits {@code expression} to stdout. */
    abstract String printCall(String expression)

    /** Tail expression that converts a {@code FileSystemLocation}-valued provider to an absolute path. */
    abstract String asFileExpression()

    /** Convenience: emits {@code "$objectType $propertyName = " + $valueExpression} followed by a print. */
    String printStatement(String objectType, String propertyName, String valueExpression) {
        return printCall("\"${objectType} ${propertyName} = \" + ${valueExpression}")
    }
}
