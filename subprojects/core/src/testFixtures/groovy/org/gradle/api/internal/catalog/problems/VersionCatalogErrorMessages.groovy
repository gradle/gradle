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

import static org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.getExcludedNames
import static org.gradle.problems.internal.RenderingUtils.oxfordListOf
import static org.gradle.util.internal.TextUtil.getPluralEnding
import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

@CompileStatic
trait VersionCatalogErrorMessages {

    void verify(String rawActual, String rawExpected) {
        String actual = normaliseLineSeparators(rawActual).replaceAll("(?m)^( +)\n", "\n").trim()
        String expected = normaliseLineSeparators(rawExpected).replaceAll("(?m)^( +)\n", "\n").trim()
        assert actual == expected
    }

    void verifyContains(String rawActual, String rawExpected) {
        String actual = normaliseLineSeparators(rawActual).replaceAll("(?m)^( +)\n", "\n").trim()
        String expected = normaliseLineSeparators(rawExpected).replaceAll("(?m)^( +)\n", "\n").trim()
        assert actual.contains(expected)
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

    String aliasContainsReservedName(@DelegatesTo(value = ReservedAlias, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(ReservedAlias, VersionCatalogProblemId.RESERVED_ALIAS_NAME, spec)
    }

    String undefinedVersionRef(@DelegatesTo(value = UndefinedVersionRef, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(UndefinedVersionRef, VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE, spec)
    }

    String undefinedAliasRef(@DelegatesTo(value = UndefinedAliasRef, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(UndefinedAliasRef, VersionCatalogProblemId.UNDEFINED_ALIAS_REFERENCE, spec)
    }

    String invalidDependencyNotation(@DelegatesTo(value = InvalidDependencyNotation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(InvalidDependencyNotation, VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION, spec)
    }

    String invalidAliasNotation(@DelegatesTo(value = InvalidAliasNotation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(InvalidAliasNotation, VersionCatalogProblemId.INVALID_ALIAS_NOTATION, spec)
    }

    String unexpectedFormatVersion(@DelegatesTo(value = UnexpectedFormatVersion, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(UnexpectedFormatVersion, VersionCatalogProblemId.UNSUPPORTED_FORMAT_VERSION, spec)
    }

    String aliasNotFinished(@DelegatesTo(value = AliasNotFinished, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(AliasNotFinished, VersionCatalogProblemId.ALIAS_NOT_FINISHED, spec)
    }

    String missingCatalogFile(@DelegatesTo(value = MissingCatalogFile, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(MissingCatalogFile, VersionCatalogProblemId.CATALOG_FILE_DOES_NOT_EXIST, spec)
    }

    String parseError(@DelegatesTo(value = ParseError, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(ParseError, VersionCatalogProblemId.TOML_SYNTAX_ERROR, spec)
    }

    String noImportFiles(@DelegatesTo(value = NoImportFiles, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(NoImportFiles, VersionCatalogProblemId.NO_IMPORT_FILES, spec)
    }

    String tooManyImportFiles(@DelegatesTo(value = NoImportFiles, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(TooManyImportFiles, VersionCatalogProblemId.TOO_MANY_IMPORT_FILES, spec)
    }

    String tooManyImportInvokation(@DelegatesTo(value = TooManyFromInvokation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        buildMessage(TooManyFromInvokation, VersionCatalogProblemId.TOO_MANY_IMPORT_INVOCATION, spec)
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
            doc.getDocumentationRecommendationFor("information", "version_catalog_problems", section)
        }

        abstract String build()
    }

    static class ParseError extends InCatalog<ParseError> {

        private List<String> errors = []

        ParseError() {
            intro = """Invalid TOML catalog definition:
"""
        }

        ParseError addError(String error) {
            errors << error
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog $catalog, parsing failed with ${errors.size()} error${getPluralEnding(errors)}.

    Reason: ${errors.join('\n    ')}.

    Possible solution: Fix the TOML file according to the syntax described at https://toml.io.

    ${documentation}"""
        }
    }

    static class NameClash extends InCatalog<NameClash> {
        private List<String> aliases = []
        private String getterName
        private String kind = 'library aliases'

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
            """${intro}  - Problem: In version catalog ${catalog}, ${kind} ${aliases.join(' and ')} are mapped to the same accessor name ${getterName}().

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
        String message
        String solution

        ReservedAlias() {
            intro = """Invalid catalog definition:
"""
        }

        ReservedAlias alias(String name) {
            this.alias = name
            this.message = "Alias '$name' is a reserved name in Gradle which prevents generation of accessors"
            this
        }

        ReservedAlias shouldNotBeEqualTo(String forbidden) {
            this.message = "Prefix for dependency shouldn't be equal to '${forbidden}'"
            this
        }

        ReservedAlias shouldNotContain(String name) {
            this.alias(name)
            this
        }

        ReservedAlias reservedAliasPrefix(String... suffixes) {
            this.solution = "Use a different alias which prefix is not equal to ${oxfordListOf(suffixes as List, 'or')}"
            this
        }

        ReservedAlias reservedAliases(String... aliases) {
            this.solution = "Use a different alias which isn't in the reserved names ${oxfordListOf(aliases as List, "or")}"
            this.reservedNames(aliases)
        }

        ReservedAlias reservedNames(String... names) {
            this.solution = "Use a different alias which doesn't contain ${getExcludedNames(names as List)}"
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, alias '${alias}' is not a valid alias.

    Reason: $message.

    Possible solution: $solution.

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

    static class UndefinedAliasRef extends InCatalog<UndefinedAliasRef> {
        String bundle
        String aliasRef

        UndefinedAliasRef() {
            intro = """Invalid catalog definition:
"""
        }

        UndefinedAliasRef bundle(String name) {
            this.bundle = name
            this
        }

        UndefinedAliasRef aliasRef(String name) {
            this.aliasRef = name
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, a bundle with name '${bundle}' declares a dependency on '${aliasRef}' which doesn't exist.

    Reason: Bundles can only contain references to existing library aliases.

    Possible solutions:
      1. Make sure that the library alias '${aliasRef}' is declared.
      2. Remove '${aliasRef}' from bundle '${bundle}'.

    ${documentation}"""
        }
    }

    static class InvalidDependencyNotation extends InCatalog<InvalidDependencyNotation> {
        String alias
        String kind = 'alias'
        String notation
        String reason
        String solution

        InvalidDependencyNotation() {
            intro = """Invalid catalog definition:
"""
        }

        InvalidDependencyNotation usingSettingsApi() {
            reason = "The 'to(String)' method only supports 'group:artifact:version' coordinates."
            solution = """    Possible solutions:
      1. Make sure that the coordinates consist of 3 parts separated by colons, eg: my.group:artifact:1.2.
      2. Use the to(group, name) method instead."""
            this
        }

        InvalidDependencyNotation alias(String alias) {
            this.alias = alias
            this
        }

        InvalidDependencyNotation kind(String kind) {
            this.kind = kind
            this
        }

        InvalidDependencyNotation invalidNotation(String notation) {
            this.notation = notation
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, on alias '${alias}' notation '${notation}' is not a valid dependency notation.

    Reason: ${reason}

${solution}

    ${documentation}"""
        }
    }

    static class InvalidAliasNotation extends InCatalog<InvalidAliasNotation> {
        String alias
        String kind = 'library'
        String notation

        InvalidAliasNotation() {
            intro = """Invalid catalog definition:
"""
        }

        InvalidAliasNotation alias(String alias) {
            this.alias = alias
            this
        }

        InvalidAliasNotation kind(String kind) {
            this.kind = kind
            this
        }

        InvalidAliasNotation invalidNotation(String notation) {
            this.notation = notation
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, invalid ${kind} alias '${notation}'.

    Reason: ${kind.capitalize()} aliases must match the following regular expression: [a-z]([a-zA-Z0-9_.\\-])+.

    Possible solution: Make sure the alias matches the [a-z]([a-zA-Z0-9_.\\-])+ regular expression.

    ${documentation}"""
        }
    }

    static class UnexpectedFormatVersion extends InCatalog<UnexpectedFormatVersion> {

        String unsupported
        String expected

        UnexpectedFormatVersion() {
            intro = """Invalid TOML catalog definition:
"""
        }

        UnexpectedFormatVersion unsupportedVersion(String v) {
            this.unsupported = v
            this
        }

        UnexpectedFormatVersion expectedVersion(String v) {
            this.expected = v
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, unsupported version catalog format ${unsupported}.

    Reason: This version of Gradle only supports format version ${expected}.

    Possible solution: Try to upgrade to a newer version of Gradle which supports the catalog format version ${unsupported}.

    ${documentation}"""
        }
    }

    static class AliasNotFinished extends InCatalog<AliasNotFinished> {

        String alias

        AliasNotFinished() {
            intro = """Invalid catalog definition:
"""
        }

        AliasNotFinished alias(String v) {
            this.alias = v
            this
        }


        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, dependency alias builder '${alias}' was not finished.

    Reason: A version was not set or explicitly declared as not wanted.

    Possible solutions:
      1. Call `.version()` to give the alias a version.
      2. Call `.withoutVersion()` to explicitly declare that the alias should not have a version.

    ${documentation}"""
        }
    }

    static class MissingCatalogFile extends InCatalog<MissingCatalogFile> {
        File missingFile

        MissingCatalogFile() {
            intro = """Invalid catalog definition:
"""
        }

        MissingCatalogFile missing(File path) {
            this.missingFile = path
            this
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, import of external catalog file failed.

    Reason: File '${missingFile.absolutePath}' doesn't exist.

    Possible solution: Make sure that the catalog file '${missingFile.name}' exists before importing it.

    ${documentation}"""
        }
    }

    static class NoImportFiles extends InCatalog<NoImportFiles> {
        NoImportFiles() {
            intro = """Invalid catalog definition:
"""
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, no files are resolved to be imported.

    Reason: The imported dependency doesn't resolve into any file.

    Possible solution: Check the import statement, it should resolve into a single file.

    ${documentation}"""
        }
    }

    static class TooManyImportFiles extends InCatalog<TooManyImportFiles> {
        TooManyImportFiles() {
            intro = """Invalid catalog definition:
"""
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, importing multiple files are not supported.

    Reason: The import consists of multiple files.

    Possible solution: Only import a single file.

    ${documentation}"""
        }
    }

    static class TooManyFromInvokation extends InCatalog<TooManyFromInvokation> {
        TooManyFromInvokation() {
            intro = """Invalid catalog definition:
"""
            section = "importing-catalog-from-file"
        }

        @Override
        String build() {
            """${intro}  - Problem: In version catalog ${catalog}, you can only call the 'from' method a single time.

    Reason: The method was called more than once.

    Possible solution: Remove further usages of the method call.

    ${documentation}"""
        }
    }
}
