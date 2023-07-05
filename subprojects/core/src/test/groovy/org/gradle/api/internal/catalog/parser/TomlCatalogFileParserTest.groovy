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
import org.gradle.api.internal.catalog.problems.VersionCatalogErrorMessages
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemTestFor
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.nio.file.Paths
import java.util.function.Supplier

class TomlCatalogFileParserTest extends Specification implements VersionCatalogErrorMessages {
    final VersionCatalogBuilder builder = new DefaultVersionCatalogBuilder("libs",
            Interners.newStrongInterner(),
            Interners.newStrongInterner(),
            TestUtil.objectFactory(),
            Stub(Supplier),
            new DefaultProblems(Stub(BuildOperationProgressEventEmitter))
    )
    DefaultVersionCatalog model

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
        InvalidUserDataException ex = thrown()
        verify(ex.message, undefinedAliasRef {
            inCatalog('libs')
            bundle('guava')
            aliasRef('hello')
        })
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
    def "fails parsing TOML file #name with reasonable error message"() {
        when:
        parse name

        then:
        InvalidUserDataException ex = thrown()
        ex.message.contains(message)

        where:
        name        | message
        'invalid1'  | "In version catalog libs, on alias 'module' notation 'foo' is not a valid dependency notation"
        'invalid2'  | "In version catalog libs, on alias 'module' module 'foo' is not a valid module notation."
        'invalid3'  | "Name for 'module' must not be empty"
        'invalid4'  | "Group for 'module' must not be empty"
        'invalid5'  | "Version for 'module' must not be empty"
        'invalid6'  | "Name for 'test' must not be empty"
        'invalid7'  | "Group for 'test' must not be empty"
        'invalid8'  | "Group for alias 'test' wasn't set"
        'invalid9'  | "Name for alias 'test' wasn't set"
        'invalid10' | "Expected a boolean but value of 'rejectAll' is a string."
        'invalid11' | "Expected an array but value of 'reject' is a table."
        'invalid12' | "In version catalog libs, unknown top level elements [toto, tata]"
        'invalid13' | "Expected an array but value of 'groovy' is a string."
        'invalid14' | "In version catalog libs, version reference 'nope' doesn't exist"
        'invalid15' | "In version catalog libs, on alias 'my' notation 'some.plugin.id' is not a valid plugin notation."
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
        InvalidUserDataException ex = thrown()
        verify(ex.message, unexpectedFormatVersion {
            inCatalog('libs')
            unsupportedVersion('999.999')
            expectedVersion('1.1')
        })
    }

    def "reasonable error message when an alias table contains unexpected key"() {
        when:
        parse "unexpected-alias-key-$i"

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "On library declaration 'guava' expected to find any of 'group', 'module', 'name', or 'version' but found unexpected ${error}."

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
        InvalidUserDataException ex = thrown()
        ex.message == "On version declaration of alias 'guava' expected to find any of 'prefer', 'ref', 'reject', 'rejectAll', 'require', or 'strictly' but found unexpected ${error}."

        where:
        i | error
        1 | "key 'rejette'"
        2 | "key 'invalid'"
        3 | "keys 'invalid' and 'rejette'"
    }

    void hasDependency(String name, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = DependencySpec) Closure<Void> spec) {
        def data = model.getDependencyData(name)
        assert data != null: "Expected a dependency with alias $name but it wasn't found"
        def dependencySpec = new DependencySpec(data)
        spec.delegate = dependencySpec
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    void hasBundle(String id, List<String> expectedElements) {
        def bundle = model.getBundle(id)?.components
        assert bundle != null: "Expected a bundle with name $id but it wasn't found"
        assert bundle == expectedElements
    }

    void hasVersion(String id, String version) {
        def versionConstraint = model.getVersion(id)?.version
        assert versionConstraint != null: "Expected a version constraint with name $id but didn't find one"
        def actual = versionConstraint.toString()
        assert actual == version
    }

    void hasPlugin(String alias, String id, String version) {
        def plugin = model.getPlugin(alias)
        assert plugin != null : "Expected a plugin with alias '$alias' but it wasn't found"
        assert plugin.id == id
        assert plugin.version.requiredVersion == version
    }

    void hasPlugin(String alias, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = PluginSpec) Closure<Void> spec) {
        def plugin = model.getPlugin(alias)
        assert plugin != null : "Expected a plugin with alias '$alias' but it wasn't found"
        def pluginSpec = new PluginSpec(plugin)
        spec.delegate = pluginSpec
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    private void parse(String name) {
        def tomlResource = getClass().getResource("/org/gradle/api/internal/catalog/parser/${name}.toml").toURI()
        // Paths might be unusual, but we need it because of 1.8
        def tomlPath = Paths.get(tomlResource)

        TomlCatalogFileParser.parse(tomlPath, builder)
        model = builder.build()
        assert model != null: "Expected model to be generated but it wasn't"
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
