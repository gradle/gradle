/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.catalog.problems

import groovy.transform.CompileStatic
import org.gradle.api.internal.DocumentationRegistry

import static org.gradle.problems.internal.RenderingUtils.oxfordListOf
import static org.gradle.util.TextUtil.normaliseLineSeparators

@CompileStatic
trait VersionCatalogErrorMessages {

    void verify(String rawActual, String rawExpected) {
        String actual = normaliseLineSeparators(rawActual).replaceAll("(?m)^( +)\n", "\n").trim()
        String expected = normaliseLineSeparators(rawExpected).replaceAll("(?m)^( +)\n", "\n").trim()
        assert actual == expected
    }

    String nameClash(@DelegatesTo(value = NameClash, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(NameClash, VersionCatalogProblemId.ACCESSOR_NAME_CLASH, spec)
    }

    String tooManyEntries(@DelegatesTo(value = TooManyEntries, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(TooManyEntries, VersionCatalogProblemId.TOO_MANY_ENTRIES, spec)
    }

    String reservedAlias(@DelegatesTo(value = ReservedAlias, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(ReservedAlias, VersionCatalogProblemId.RESERVED_ALIAS_NAME, spec)
    }

    String undefinedVersionRef(@DelegatesTo(value = UndefinedVersionRef, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(UndefinedVersionRef, VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE, spec)
    }

    private static <T extends InCatalog<T>> String buildMessage(Class<T> clazz, VersionCatalogProblemId id, Closure<?> spec) {
        def desc = clazz.newInstance()
        desc.section = id.name().toLowerCase()
        spec.delegate = desc
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        desc.build()
    }

    abstract static class InCatalog<T extends InCatalog<T>> {
        protected final DocumentationRegistry doc = new DocumentationRegistry()
        String section

        protected String catalog = 'lib'
        protected String intro = """Cannot generate dependency accessors:
"""

        T inCatalog(String name) {
            this.catalog = name
            this as T
        }

        T noIntro() {
            intro = ''
            this as T
        }

        String getDocumentation() {
            "Please refer to ${doc.getDocumentationFor("version_catalog_problems", section)} for more details about this problem."
        }

        abstract String build()
    }

    static class NameClash extends InCatalog<NameClash> {
        private List<String> aliases = []
        private String getterName
        private String kind = 'aliases'

        NameClash inConflict(String... aliases) {
            Collections.addAll(this.aliases, aliases)
            this
        }

        NameClash getterName(String getter) {
            this.getterName = getter
            this
        }

        NameClash kind(String kind) {
            this.kind = kind
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, dependency ${kind} ${aliases.join(' and ')} are mapped to the same accessor name ${getterName}().

    Reason: A name clash was detected.

    Possible solution: Use a different alias for ${aliases.join(' and ')}.

    ${documentation}"""
        }
    }

    static class TooManyEntries extends InCatalog<TooManyEntries> {
        private int maxCount = 30_000
        private int entryCount

        TooManyEntries entryCount(int count) {
            this.entryCount = count
            this
        }

        TooManyEntries maxCount(int count) {
            this.maxCount = count
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, version catalog model contains too many entries (${entryCount}).

    Reason: The maximum number of aliases in a catalog is ${maxCount}.

    Possible solutions:
      1. Reduce the number of aliases defined in this catalog.
      2. Split the catalog into multiple catalogs.

    ${documentation}"""
        }
    }

    static class ReservedAlias extends InCatalog<ReservedAlias> {
        String alias
        String shouldNotEndWith
        List<String> reservedAliasSuffixes = []

        ReservedAlias() {
            intro = """Invalid catalog definition:
"""
        }

        ReservedAlias alias(String name) {
            this.alias = name
            this
        }

        ReservedAlias shouldNotEndWith(String forbidden) {
            this.shouldNotEndWith = forbidden
            this
        }

        ReservedAlias reservedAliasSuffix(String... suffixes) {
            Collections.addAll(reservedAliasSuffixes, suffixes)
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, alias '${alias}' is not a valid alias.

    Reason: It shouldn't end with '${shouldNotEndWith}'.

    Possible solution: Use a different alias which doesn't end with ${oxfordListOf(reservedAliasSuffixes, 'or')}.

    ${documentation}"""
        }
    }

    static class UndefinedVersionRef extends InCatalog<UndefinedVersionRef> {
        String dependency
        String versionRef
        List<String> existingVersionRefs = []

        UndefinedVersionRef() {
            intro = """Invalid catalog definition:
"""
        }

        UndefinedVersionRef versionRef(String name) {
            this.versionRef = name
            this
        }

        UndefinedVersionRef existing(String... names) {
            Collections.addAll(existingVersionRefs, names)
            this
        }

        UndefinedVersionRef forDependency(String group, String name) {
            this.dependency = "$group:$name"
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, version reference '${versionRef}' doesn't exist.

    Reason: Dependency '${dependency}' references version '${versionRef}' which doesn't exist.

    Possible solutions:
      1. Declare '${versionRef}' in the catalog.
      2. Use one of the following existing versions: ${oxfordListOf(existingVersionRefs, 'or')}.

    ${documentation}"""
        }
    }
}
