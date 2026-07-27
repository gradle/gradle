/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.catalog.parser

import com.google.common.collect.Interners
import groovy.transform.CompileStatic
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
import org.gradle.api.internal.catalog.DependencyModel
import org.gradle.api.internal.catalog.PluginModel

import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemTestFor
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.nio.file.Paths
import java.util.function.Supplier

class TomlCatalogFileParserTest extends Specification {

    def supplier = Stub(Supplier)
    def problems = TestUtil.problemsService()
    def createVersionCatalogBuilder() {
        new DefaultVersionCatalogBuilder(
            "libs",
            Interners.newStrongInterner(),
            Interners.newStrongInterner(),
            TestUtil.objectFactory(),
            supplier) {
            @Override
            protected ProblemsInternal getProblemsService() {
                return problems
            }
        }
    }

    final VersionCatalogBuilder builder = createVersionCatalogBuilder()
    DefaultVersionCatalog model

    def setup() {
        problems.resetRecordedProblems()
    }

    def "parses a file with a single dependency and nothing else"() {
        when:
        parse('one-dependency')

        then:
        hasDependency('guava') {
            withGroup 'com.google.guava'
            withName 'guava'
            withVersion {
                require '18.0-jre'
            }
        }
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.UNDEFINED_ALIAS_REFERENCE
    )
    def "parses a file with a single bundle and nothing else"() {
        when:
        parse('one-bundle')

        then:
        thrown(InvalidUserDataException)
        problems.assertProblemEmittedOnce() {
            it.definition.id.displayName == "Bundle declares dependency on non-existent alias"
            it.contextualLabel == "In version catalog libs, a bundle with name 'guava' declares a dependency on 'hello' which doesn't exist"
            it.details == "Bundles can only contain references to existing library aliases."
            it.solutions == ["Make sure that the library alias 'hello' is declared", "Remove 'hello' from bundle 'guava'."]
            it.definition.documentationLink.url.endsWith('userguide/version_catalog_problems.html#undefined_alias_reference')
        }
    }

    def "parses a file with a single version and nothing else"() {
        when:
        parse('one-version')

        then:
        hasVersion('guava', '17')
    }

    def "parses a file with a single plugin and nothing else"() {
        when:
        parse('one-plugin')

        then:
        hasPlugin('greeter', 'org.example.greeter', '1.13')
    }

    def "parses dependencies with various notations"() {
        when:
        parse 'dependency-notations'

        then:
        hasDependency('simple') {
            withGAV('foo', 'bar', '1.0')
        }
        hasDependency('simple-with-rich1') {
            withGroup 'foo'
            withName 'bar'
            withVersion {
                prefer '1.0'
            }
        }
        hasDependency('simple-with-rich2') {
            withGroup 'foo'
            withName 'bar'
            withVersion {
                prefer '1.0'
            }
        }
        hasDependency('simple-with-rich3') {
            withGroup 'foo'
            withName 'bar'
            withVersion {
                require '1.0'
            }
        }
        hasDependency('simple-with-rich4') {
            withGroup 'foo'
            withName 'bar'
            withVersion {
                strictly '1.0'
            }
        }
        hasDependency('simple-with-rich5') {
            withGroup 'foo'
            withName 'bar'
            withVersion {
                rejectAll()
            }
        }
        hasDependency('simple-with-rich6') {
            withGroup 'foo'
            withName 'bar'
            withVersion {
                require '1.0'
                reject('1.1', '1.2')
            }
        }
        hasDependency('shortcut') {
            withGAV('g', 'a', '1.0')
        }
        hasDependency('indirect') {
            withGAV('g', 'b', '1.2')
        }
        hasDependency('incremental') {
            withGroup 'group'
            withName 'name'
            withVersion {
                require '[1.0, 2.0['
                prefer '1.1'
            }
        }
        hasDependency('incremental2') {
            withGroup 'group'
            withName 'name'
            withVersion {
                strictly '[1.0, 2.0['
                prefer '1.5'
            }
        }
        hasDependency('groovy-with-ref') {
            withGroup 'org.codehaus.groovy'
            withName 'groovy'
            withVersion {
                require '2.5.6'
            }
        }
        hasDependency('groovy-with-ref2') {
            withGroup 'org.codehaus.groovy'
            withName 'groovy'
            withVersion {
                strictly '[2.5, 3.0['
                prefer '2.5.6'
            }
        }
        hasDependency('groovy-with-ref3') {
            withGroup 'org.codehaus.groovy'
            withName 'groovy'
            withVersion {
                strictly '1.7'
            }
        }
        hasDependency('strict-with-bang') {
            withGroup('g')
            withName('a')
            withVersion {
                strictly '1.1'
            }
        }
        hasDependency('strict-with-bang-and-range') {
            withGroup('g')
            withName('a')
            withVersion {
                strictly '[1.0,2.0]'
                prefer '1.1'
            }
        }
    }

    def "parses plugins with different notations"() {
        when:
        parse('plugin-notations')

        then:
        hasPlugin('simple', 'org.example', '1.0')
        hasPlugin('without.version', 'org.example', '')
        hasPlugin('with.id', 'org.example', '1.1')
        hasPlugin('with.ref') {
            withId 'org.example'
            withVersionRef 'ref'
            withVersion {
                require '1.6'
            }
        }
        hasPlugin('with-rich1') {
            withId 'org.example'
            withVersion {
                prefer '1.0'
            }
        }
        hasPlugin('with-rich2') {
            withId 'org.example'
            withVersion {
                prefer '1.0'
            }
        }
        hasPlugin('with-rich3') {
            withId 'org.example'
            withVersion {
                require '1.0'
            }
        }
        hasPlugin('with-rich4') {
            withId 'org.example'
            withVersion {
                strictly '1.0'
            }
        }
        hasPlugin('with-rich5') {
            withId 'org.example'
            withVersion {
                rejectAll()
            }
        }
        hasPlugin('with-rich6') {
            withId 'org.example'
            withVersion {
                require '1.0'
                reject '1.1', '1.2'
            }
        }
        hasPlugin('indirect') {
            withId 'org.example'
            withVersion {
                require '1.5'
            }
        }
    }

    def "parses bundles"() {
        when:
        parse 'with-bundles'

        then:
        hasDependency('groovy') {
            withGAV("org.codehaus.groovy:groovy:2.5.6")
        }
        hasDependency('groovy-json') {
            withGAV("org.codehaus.groovy:groovy-json:2.5.6")
        }
        hasDependency('groovy-templates') {
            withGAV("org.codehaus.groovy:groovy-templates:2.5.6")
        }
        hasBundle('groovy', ['groovy.json', 'groovy', 'groovy.templates'])
    }

    @VersionCatalogProblemTestFor([
        VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE,
        VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION,
        VersionCatalogProblemId.TOML_SYNTAX_ERROR,
        VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION
    ])
    def "fails parsing TOML file #name with reasonable error"() {
        when:
        parse name

        then:
        thrown(InvalidUserDataException)
        problems.assertProblemEmittedOnce {
            it.contextualLabel == label
            it.details == details
        }

        where:
        name        | label                                                                                             | details
        'invalid1'  | "In version catalog libs, on alias 'module' notation 'foo' is not a valid dependency notation"    | "When using a string to declare library coordinates, you must use a valid dependency notation"
        'invalid2'  | "In version catalog libs, on alias 'module' module 'foo' is not a valid module notation"          | "When using a string to declare library module coordinates, you must use a valid module notation"
        'invalid3'  | "Alias definition 'module' is invalid"                                                            | "Empty name for plugin alias 'module'Name for 'module' must not be empty"
        'invalid4'  | "Alias definition 'module' is invalid"                                                            | "Empty group for plugin alias 'module'Group for 'module' must not be empty"
        'invalid5'  | "Alias definition 'module' is invalid"                                                            | "Empty version for plugin alias 'module'Version for 'module' must not be empty"
        'invalid6'  | "Alias definition 'test' is invalid"                                                              | "Empty name for plugin alias 'test'Name for 'test' must not be empty"
        'invalid7'  | "Alias definition 'test' is invalid"                                                              | "Empty group for plugin alias 'test'Group for 'test' must not be empty"
        'invalid8'  | "Alias definition 'test' is invalid"                                                              | "Group for alias 'test' wasn't set"
        'invalid9'  | "Alias definition 'test' is invalid"                                                              | "Name for alias 'test' wasn't set"
        'invalid10' | "Unexpected type for alias 'test'"                                                                | "Expected a boolean but value of 'rejectAll' is a string"
        'invalid11' | "Unexpected type for alias 'test'"                                                                | "Expected an array but value of 'reject' is a table"
        'invalid12' | "In version catalog libs, unknown top level elements [toto, tata]"                                | "TOML file contains an unexpected top-level element"
        'invalid13' | "Unexpected type for bundle 'groovy'"                                                             | "Expected an array but value of 'groovy' is a string"
        'invalid14' | "In version catalog libs, version reference 'nope' doesn't exist"                                 | "Dependency 'com:foo' references version 'nope' which doesn't exist"
        'invalid15' | "In version catalog libs, on alias 'my' notation 'some.plugin.id' is not a valid plugin notation" | "When using a string to declare plugin coordinates, you must use a valid plugin notation"

    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.TOML_SYNTAX_ERROR
    )
    def "fails parsing TOML file with multiple syntax errors"() {
        when:
        parse 'invalid16'

        then:
        thrown(InvalidUserDataException)
        def emitted = problems.emitted
        emitted.size() == 2
        verifyAll {
            emitted[0].contextualLabel == 'Unexpected end of line, expected \', ", \'\'\', """, a number, a boolean, a date/time, an array, or a table'
            emitted[0].details == "TOML syntax invalid"
            emitted[1].contextualLabel == "Unexpected end of line, expected ', \", ''', \"\"\", a number, a boolean, a date/time, an array, or a table"
            emitted[1].details == "TOML syntax invalid"
        }
    }

    def "supports dependencies without version"() {
        when:
        parse 'without-version'

        then:
        hasDependency("alias1") {
            withGAV("g:a:")
        }
        hasDependency("alias2") {
            withGAV("g:a:")
        }
        hasDependency("alias3") {
            withGAV("g:a:")
        }
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.UNSUPPORTED_FORMAT_VERSION
    )
    def "reasonable error message if a file format isn't supported"() {
        when:
        parse 'unsupported-format'

        then:
        thrown(InvalidUserDataException)
        problems.assertProblemEmittedOnce {
            it.definition.id.displayName == "Unsupported format version"
            it.contextualLabel == "In version catalog libs, unsupported version catalog format 999.999"
            it.details == "This version of Gradle only supports format version 1.1"
            it.solutions == ["Try to upgrade to a newer version of Gradle which supports the catalog format version 999.999."]
            it.definition.documentationLink.url.endsWith('userguide/version_catalog_problems.html#unsupported_format_version')
        }
    }

    def "reasonable error message when an alias table contains unexpected key"() {
        when:
        parse "unexpected-alias-key-$i"

        then:
        thrown(InvalidUserDataException)
        problems.assertProblemEmittedOnce {
            it.definition.id.displayName == "Invalid TOML definition"
            it.contextualLabel == "On library declaration 'guava' expected to find any of 'group', 'module', 'name', or 'version' but found unexpected ${error}"
            it.details == "TOML file contains an unexpected key in a known table"
            it.solutions == ["Remove the unexpected key, or use one of 'group', 'module', 'name', or 'version'"]
            it.definition.documentationLink.url.endsWith('userguide/version_catalog_problems.html#invalid_toml_definition')
        }

        where:
        i | error
        1 | "key 'invalid'"
        2 | "key 'invalid'"
        3 | "keys 'extra' and 'invalid'"
    }

    def "reasonable error message when a version table contains unexpected key"() {
        when:
        parse "unexpected-version-key-$i"

        then:
        thrown(InvalidUserDataException)
        problems.assertProblemEmittedOnce {
            it.definition.id.displayName == "Invalid TOML definition"
            it.contextualLabel == "On version declaration of alias 'guava' expected to find any of 'prefer', 'ref', 'reject', 'rejectAll', 'require', or 'strictly' but found unexpected ${error}"
            it.details == "TOML file contains an unexpected key in a known table"
            it.solutions == ["Remove the unexpected key, or use one of 'prefer', 'ref', 'reject', 'rejectAll', 'require', or 'strictly'"]
            it.definition.documentationLink.url.endsWith('userguide/version_catalog_problems.html#invalid_toml_definition')
        }

        where:
        i | error
        1 | "key 'rejette'"
        2 | "key 'invalid'"
        3 | "keys 'invalid' and 'rejette'"
    }

    void hasDependency(String name, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = DependencySpec) Closure<Void> spec) {
        assert model.hasDependency(name)
        def data = model.getDependencyData(name)
        assert data != null: "Expected a dependency with alias $name but it wasn't found"
        def dependencySpec = new DependencySpec(data)
        spec.delegate = dependencySpec
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    void hasBundle(String id, List<String> expectedElements) {
        assert model.hasBundle(id)
        def bundle = model.getBundle(id)?.components
        assert bundle != null: "Expected a bundle with name $id but it wasn't found"
        assert bundle == expectedElements
    }

    void hasVersion(String id, String version) {
        assert model.hasVersion(id)
        def versionConstraint = model.getVersion(id)?.version
        assert versionConstraint != null: "Expected a version constraint with name $id but didn't find one"
        def actual = versionConstraint.toString()
        assert actual == version
    }

    void hasPlugin(String alias, String id, String version) {
        assert model.hasPlugin(alias)
        def plugin = model.getPlugin(alias)
        assert plugin != null: "Expected a plugin with alias '$alias' but it wasn't found"
        assert plugin.id == id
        assert plugin.version.requiredVersion == version
    }

    void hasPlugin(String alias, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = PluginSpec) Closure<Void> spec) {
        assert model.hasPlugin(alias)
        def plugin = model.getPlugin(alias)
        assert plugin != null: "Expected a plugin with alias '$alias' but it wasn't found"
        def pluginSpec = new PluginSpec(plugin)
        spec.delegate = pluginSpec
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    private void parse(String name) {
        def tomlPath = getTomlPath(name)

        TomlCatalogFileParser.parse(tomlPath, builder, { problems })
        model = builder.build()
        assert model != null: "Expected model to be generated but it wasn't"
    }

    private getTomlPath(String name) {
        def tomlResource = getClass().getResource("/org/gradle/api/internal/catalog/parser/${name}.toml").toURI()
        // Paths might be unusual, but we need it because of 1.8
        Paths.get(tomlResource)
    }

    @CompileStatic
    private static class DependencySpec {
        private final DependencyModel model

        DependencySpec(DependencyModel model) {
            this.model = model
        }

        void withGroup(String group) {
            assert model.group == group
        }

        void withName(String name) {
            assert model.name == name
        }

        void withGAV(String gav) {
            def coord = gav.split(':')
            def v = coord.length > 2 ? coord[2] : ''
            withGAV(coord[0], coord[1], v)
        }

        void withGAV(String group, String name, String version) {
            withGroup group
            withName name
            withVersion {
                require version
            }
        }

        void withVersion(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MutableVersionConstraint) Closure<Void> spec) {
            def mutable = new DefaultMutableVersionConstraint("")
            spec.delegate = mutable
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec.call()
            def version = DefaultImmutableVersionConstraint.of(mutable)
            assert model.version == version
        }
    }

    @CompileStatic
    private static class PluginSpec {
        private final PluginModel model

        PluginSpec(PluginModel model) {
            this.model = model
        }

        void withId(String id) {
            assert model.id == id
        }

        void withVersionRef(String ref) {
            assert model.versionRef == ref
        }

        void withVersion(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MutableVersionConstraint) Closure<Void> spec) {
            def mutable = new DefaultMutableVersionConstraint("")
            spec.delegate = mutable
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec.call()
            def version = DefaultImmutableVersionConstraint.of(mutable)
            assert model.version == version
        }
    }
}
