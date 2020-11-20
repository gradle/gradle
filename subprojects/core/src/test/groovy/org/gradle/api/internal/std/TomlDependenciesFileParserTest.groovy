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

package org.gradle.api.internal.std

import com.google.common.collect.Interners
import groovy.transform.CompileStatic
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nullable
import java.util.function.Supplier

import static org.gradle.api.internal.std.IncludeExcludePredicate.acceptAll

class TomlDependenciesFileParserTest extends Specification {
    final ImportConfiguration importConf = new ImportConfiguration(acceptAll(), acceptAll(), acceptAll(), acceptAll())
    final VersionCatalogBuilder builder = new DefaultVersionCatalogBuilder("libs",
        Interners.newStrongInterner(),
        Interners.newStrongInterner(),
        TestUtil.objectFactory(),
        TestUtil.providerFactory(),
        Stub(PluginDependenciesSpec),
        Stub(Supplier),
    )
    final Map<String, TestPlugin> plugins = [:].withDefault { new TestPlugin() }
    final PluginDependenciesSpec pluginsSpec = new PluginDependenciesSpec() {
        @Override
        PluginDependencySpec id(String id) {
            plugins[id]
        }
    }
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

    def "parses a file with a single bundle and nothing else"() {
        when:
        parse('one-bundle')

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "A bundle with name 'guava' declares a dependency on 'hello' which doesn't exist"
    }

    def "parses a file with a single plugin and nothing else"() {
        when:
        parse('one-plugin')

        then:
        hasPlugin('org.gradle.test.my-plugin', '1.0')
    }

    def "parses a file with a single version and nothing else"() {
        when:
        parse('one-version')

        then:
        hasVersion('guava', '17')
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
        hasBundle('groovy', ['groovy-json', 'groovy', 'groovy-templates'])
    }

    @Unroll
    def "fails parsing TOML file #name with reasonable error message"() {
        when:
        parse name

        then:
        InvalidUserDataException ex = thrown()
        ex.message == message

        where:
        name        | message
        'invalid1'  | "Invalid GAV notation 'foo' for alias 'module': it must consist of 3 parts separated by colons, eg: my.group:artifact:1.2"
        'invalid2'  | "Invalid module notation 'foo' for alias 'module': it must consist of 2 parts separated by colons, eg: my.group:artifact"
        'invalid3'  | "Name for 'module' must not be empty"
        'invalid4'  | "Group for 'module' must not be empty"
        'invalid5'  | "Version for 'module' must not be empty"
        'invalid6'  | "Name for 'test' must not be empty"
        'invalid7'  | "Group for 'test' must not be empty"
        'invalid8'  | "Group for alias 'test' wasn't set"
        'invalid9'  | "Name for alias 'test' wasn't set"
        'invalid10' | "On alias 'test' expected a boolean but value of 'rejectAll' is a string"
        'invalid11' | "On alias 'test' expected an array but value of 'reject' is a table"
        'invalid12' | "Unknown top level elements [toto, tata]"
        'invalid13' | "On bundle 'groovy' expected an array but value of 'groovy' is a string"
        'invalid14' | "On plugin 'my.awesome.plugin' expected a string but value of 'my.awesome.plugin' is a boolean"
        'invalid15' | "Referenced version 'nope' doesn't exist on dependency com:foo"
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

    void hasPlugin(String id, String version = '1.0') {
        assert plugins.containsKey(id) && plugins[id].version == version
    }

    void hasVersion(String id, String version) {
        def versionConstraint = model.getVersion(id)?.version
        assert versionConstraint != null : "Expected a version constraint with name $id but didn't find one"
        def actual = versionConstraint.toString()
        assert actual == version
    }

    private void parse(String name) {
        TomlDependenciesFileParser.parse(toml(name), builder, pluginsSpec, importConf)
        model = builder.build()
        assert model != null: "Expected model to be generated but it wasn't"
    }

    private static InputStream toml(String name) {
        return TomlDependenciesFileParserTest.class.getResourceAsStream("${name}.toml").withReader("utf-8") {
            String text = it.text
            // we're using an in-memory input stream to make sure we don't accidentally leak descriptors in tests
            return new ByteArrayInputStream(text.getBytes("utf-8"))
        }
    }

    @CompileStatic
    private static class TestPlugin implements PluginDependencySpec {
        String version
        String apply

        @Override
        PluginDependencySpec version(@Nullable String version) {
            this.version = version
            return this
        }

        @Override
        PluginDependencySpec apply(boolean apply) {
            this.apply = apply
            this
        }
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
            def v = coord.length>2 ? coord[2] : ''
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
}
