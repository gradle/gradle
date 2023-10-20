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

package org.gradle.api.internal.catalog

import com.google.common.collect.Interners
import groovy.transform.Canonical
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.catalog.problems.VersionCatalogErrorMessages
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemTestFor
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.internal.management.VersionCatalogBuilderInternal
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.service.scopes.Scopes
import org.gradle.process.ExecOperations
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Issue

import java.util.function.Supplier

import static org.gradle.api.internal.catalog.AbstractSourceGenerator.toJavaName

class LibrariesSourceGeneratorTest extends AbstractVersionCatalogTest implements VersionCatalogErrorMessages {

    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get())
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))
    private final ClassPath classPath = classPathRegistry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER")

    @Rule
    private final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    private GeneratedSource sources
    final ObjectFactory objects = TestUtil.objectFactory()
    final ImmutableAttributesFactory attributesFactory = AttributeTestUtil.attributesFactory()
    final CapabilityNotationParser capabilityNotationParser = new CapabilityNotationParserFactory(false).create()
    final ProviderFactory providerFactory = new DefaultProviderFactory(
        new DefaultValueSourceProviderFactory(
            new DefaultListenerManager(Scopes.Build),
            TestUtil.instantiatorFactory(),
            new TestIsolatableFactory(),
            Stub(GradleProperties),
            Stub(ExecOperations),
            TestUtil.services()
        ),
        null,
        null
    )

    def "generates sources for empty model"() {
        when:
        generate('Libs') {
            // nothing
        }

        then:
        sources.assertClass('Libs')
    }

    def "generates an accessor for #name as method #method"() {
        when:
        generate {
            library(name, 'g:a:v')
        }

        then:
        sources.hasDependencyAlias(name, method)

        where:
        name                  | method
        'groovy'              | 'getGroovy'
        'groovy-json'         | 'getJson'
        'groovy.json'         | 'getJson'
        'groovyJson'          | 'getGroovyJson'
        'lang3'               | 'getLang3'
        'kotlinx.awesome.lib' | 'getLib'
    }

    def "generates an accessor for bundle #name as method #method"() {
        when:
        generate {
            library('foo', 'g:a:v')
            library('bar', 'g:a:v')
            bundle(name, ['foo', 'bar'])
        }

        then:
        sources.hasBundle(name, method)

        where:
        name          | method
        'test'        | 'getTest'
        'testBundle'  | 'getTestBundle'
        'test.bundle' | 'getBundle'
        'test.json'   | 'getJson'
        'a.b'         | 'getB'
    }

    def "generates an accessor for #name as version #method"() {
        when:
        generate {
            version(name, '1.0')
        }

        then:
        sources.hasVersion(name, method)

        where:
        name            | method
        'groovy'        | 'getGroovy'
        'groovyVersion' | 'getGroovyVersion'
        'groovy-json'   | 'getJson'
        'groovy.json'   | 'getJson'
        'groovyJson'    | 'getGroovyJson'
        'lang3Version'  | 'getLang3Version'
    }

    def "generates version info in javadoc for dependencies and plugins"() {
        when:
        generate {
            version('barVersion', '2.0')
            library('foo', 'group:foo:1.0')
            library('fooBaz', 'group', 'foo-baz').version {
                it.prefer('1.2')
                it.strictly('[1.0, 2.0[')
            }
            library('bar', 'group', 'bar').versionRef('barVersion')
            plugin('fooPlugin', 'org.foo.plugin').version('1.0')
            plugin('barPlugin', 'org.bar.plugin').versionRef('barVersion')
        }

        then:
        sources.hasDependencyAlias('foo', 'getFoo', "with version '1.0'")
        sources.hasDependencyAlias('fooBaz', 'getFooBaz', "with version '{strictly [1.0, 2.0[; prefer 1.2}'")
        sources.hasDependencyAlias('bar', 'getBar', "with versionRef 'barVersion'")

        sources.hasPlugin('fooPlugin', 'getFooPlugin', "with version '1.0'")
        sources.hasPlugin('barPlugin', 'getBarPlugin', "with versionRef 'barVersion'")
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.ACCESSOR_NAME_CLASH
    )
    def "reasonable error message if methods have the same name"() {
        when:
        generate {
            library('groovy.json', 'g:a:v')
            library('groovyJson', 'g:a:v')
        }

        then:
        InvalidUserDataException ex = thrown()
        verify ex.message, nameClash {
            inConflict('groovy.json', 'groovyJson')
            getterName('getGroovyJson')
        }

        when:
        generate {
            library('groovy.json', 'g:a:v')
            library('groovyJson', 'g:a:v')

            library('tada_one', 'g:a:v')
            library('tada.one', 'g:a:v')
            library('tadaOne', 'g:a:v')
        }

        then:
        ex = thrown()
        verify(ex.message, """Cannot generate dependency accessors:
${nameClash { noIntro().inConflict('groovy.json', 'groovyJson').getterName('getGroovyJson') }}
${nameClash { noIntro().inConflict('tada.one', 'tadaOne').getterName('getTadaOne') }}
""")
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.ACCESSOR_NAME_CLASH
    )
    def "reasonable error message if bundles have the same name"() {
        when:
        generate {
            library('foo', 'g:a:v')
            library('bar', 'g:a:v')
            bundle('one.cool', ['foo', 'bar'])
            bundle('oneCool', ['foo', 'bar'])
        }

        then:
        InvalidUserDataException ex = thrown()
        verify(ex.message, nameClash {
            kind('dependency bundles')
            inConflict('one.cool', 'oneCool')
            getterName('getOneCoolBundle')
        })

        when:
        generate {
            library('foo', 'g:a:v')
            library('bar', 'g:a:v')
            bundle('one.cool', ['foo', 'bar'])
            bundle('oneCool', ['foo', 'bar'])

            bundle("other.cool", ['foo'])
            bundle("other_cool", ['bar'])
            bundle("otherCool", ['bar'])
        }

        then:
        ex = thrown()
        verify(ex.message, """Cannot generate dependency accessors:
${nameClash { noIntro().kind('dependency bundles').inConflict('other.cool', 'otherCool').getterName('getOtherCoolBundle') }}
${nameClash { noIntro().kind('dependency bundles').inConflict('one.cool', 'oneCool').getterName('getOneCoolBundle') }}
""")
    }

    def "generated sources can be compiled"() {
        when:
        generate {
            library('foo', 'g:a:v')
            library('bar', 'g2:a2:v2')
            bundle('myBundle', ['foo', 'bar'])
            plugin('pl', 'org.plugin') version('1.2')
        }

        then:
        def libs = sources.compile()
        def foo = libs.foo.get()
        def bar = libs.bar.get()
        assert foo.module.group == 'g'
        assert foo.module.name == 'a'
        assert foo.versionConstraint.requiredVersion == 'v'

        assert bar.module.group == 'g2'
        assert bar.module.name == 'a2'
        assert bar.versionConstraint.requiredVersion == 'v2'

        def bundle = libs.bundles.myBundle.get()
        assert bundle == [foo, bar]

        def plugin = libs.plugins.pl.get()
        plugin.pluginId == 'org.plugin'
        plugin.version.requiredVersion == '1.2'
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    def "reasonable error message in case a reserved alias name is used"() {
        when:
        generate {
            library(reservedName, "org:test:1.0")
        }

        then:
        InvalidUserDataException ex = thrown()
        verify ex.message, reservedAlias {
            alias(reservedName).shouldNotBeEqualTo(prefix)
            reservedAliasPrefix('bundles', 'plugins', 'versions')
        }

        where:
        reservedName | prefix
        "bundles"    | "bundles"
        "versions"   | "versions"
        "plugins"    | "plugins"
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.TOO_MANY_ENTRIES
    )
    def "reasonable error message if an alias uses a reserved name"() {
        when:
        generate {
            16000.times { n ->
                library("alias$n", "g:a$n:1.0")
                bundle("foo$n", ["alias$n".toString()])
            }
        }

        then:
        InvalidUserDataException ex = thrown()
        verify ex.message, tooManyEntries {
            entryCount(32000)
        }
    }

    def "outputs context in javadocs"() {
        def context = "some plugin"
        def innerContext = "some inner plugin"
        when:
        generate {
            description.set("Some description for tests")
            withContext(context) {
                library("some-alias", 'g:a:v')
                bundle("b0Bundle", ["some-alias"])
                withContext(innerContext) {
                    version("v0Version", "1.0")
                }
                library("other", "g", "a").versionRef("v0Version")
            }
        }

        then:
        sources.assertClass('Generated', 'Some description for tests')
        sources.hasDependencyAlias('some-alias', 'getAlias', "This dependency was declared in ${context}")
        sources.hasBundle('b0Bundle', 'getB0Bundle', "This bundle was declared in ${context}")
        sources.hasVersion('v0Version', 'getV0Version', "This version was declared in ${innerContext}")
        sources.hasDependencyAlias('other', 'getOther', "This dependency was declared in ${context}")
    }

    @Issue("https://github.com/gradle/gradle/issues/19752")
    def "backslashes are escaped when outputting context in javadocs"() {
        def context = "Windows path: C:\\Users\\user\\Documents\\ultimate plugin"
        def escapedContext = "Windows path: C:\\Users\\u005cuser\\Documents\\u005cultimate plugin"

        when:
        generate {
            description.set("Some description for tests")
            withContext(context) {
                library("some-alias", 'g:a:v')
                bundle("b0Bundle", ["some-alias"])
                version("v0Version", "1.0")
                library("other", "g", "a").versionRef("v0Version")
            }
        }

        then:
        sources.assertClass('Generated', 'Some description for tests')
        sources.hasDependencyAlias('some-alias', 'getAlias', "This dependency was declared in ${escapedContext}")
        sources.hasBundle('b0Bundle', 'getB0Bundle', "This bundle was declared in ${escapedContext}")
        sources.hasVersion('v0Version', 'getV0Version', "This version was declared in ${escapedContext}")
        sources.hasDependencyAlias('other', 'getOther', "This dependency was declared in ${escapedContext}")
    }

    @Issue("https://github.com/gradle/gradle/issues/20060")
    def "no name conflicting of library accessors"() {
        when:
        generate {
            library('com-company-libs-a', 'com.company:libs-a:1.0')
            library('com-companylibs-b', 'com.companylibs:libs-b:1.0')
        }

        then:
        sources.hasLibraryAccessor('ComCompanyLibraryAccessors')
        sources.hasLibraryAccessor('ComCompanylibsLibraryAccessors')
        sources.hasLibraryAccessor('ComCompanyLibsLibraryAccessors$1')
    }

    private void generate(String className = 'Generated', @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = VersionCatalogBuilderInternal) Closure<Void> spec) {
        def stub = Stub(BuildOperationProgressEventEmitter)
        def problems = new DefaultProblems(stub)
        DefaultVersionCatalogBuilder builder = new DefaultVersionCatalogBuilder(
            "lib",
            Interners.newStrongInterner(),
            Interners.newStrongInterner(),
            TestUtil.objectFactory(),
            Stub(Supplier)) {
            @Override
            protected Problems getProblemService() {
                problems
            }
        }
        spec.delegate = builder
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        def writer = new StringWriter()
        def model = builder.build()
        LibrariesSourceGenerator.generateSource(writer, model, 'org.test', className, problems)
        sources = new GeneratedSource(className, writer.toString(), model)
    }

    class GeneratedSource {
        final String className
        final String source
        final DefaultVersionCatalog model
        final List<String> lines

        Class<? extends AbstractExternalDependencyFactory> factory

        GeneratedSource(String className, String src, DefaultVersionCatalog model) {
            this.className = className
            this.source = src
            this.model = model
            this.lines = src.split(System.getProperty("line.separator")) as List<String>
        }

        void assertClass(String name, String javadoc = null) {
            def result = Lookup.find(lines, "public class $name extends AbstractExternalDependencyFactory {")
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        void hasLibraryAccessor(String name) {
            def result = Lookup.find(lines, "public static class $name extends SubDependencyFactory {")
            assert result.match
        }

        void hasDependencyAlias(String name, String methodName = "get${toJavaName(name)}", String javadoc = null) {
            def lookup = "public Provider<MinimalExternalModuleDependency> $methodName() {"
            def result = Lookup.find(lines, lookup)
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        void hasBundle(String name, String methodName = "get${toJavaName(name)}Bundle", String javadoc = null) {
            def lookup = "public Provider<ExternalModuleDependencyBundle> $methodName() {"
            def result = Lookup.find(lines, lookup)
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        void hasVersion(String name, String methodName = "get${toJavaName(name)}Version", String javadoc = null) {
            def lookup = "public Provider<String> $methodName() {"
            def result = Lookup.find(lines, lookup)
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        void hasPlugin(String name, String methodName = "get${toJavaName(name)}", String javadoc = null) {
            def lookup = "public Provider<PluginDependency> $methodName() {"
            def result = Lookup.find(lines, lookup)
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        Object compile() {
            def srcDir = tmpDir.createDir("src")
            def dstDir = tmpDir.createDir("dst")
            SimpleGeneratedJavaClassCompiler.compile(srcDir, dstDir, [new TestClassSource(className, source)], classPath)
            def cl = new URLClassLoader([dstDir.toURI().toURL()] as URL[], this.class.classLoader)
            factory = cl.loadClass("org.test.$className")
            assert factory
            factory.newInstance(model, providerFactory, objects, attributesFactory, capabilityNotationParser)
        }
    }

    @Canonical
    private static class Lookup {
        final boolean match
        final List<String> javadoc

        static Lookup find(List<String> lines, String anchor) {
            boolean inJavadoc
            List<String> javadoc = []
            for (line in lines) {
                if (line.trim().startsWith("/**")) {
                    inJavadoc = true
                    javadoc.clear()
                }
                if (inJavadoc) {
                    javadoc << line
                }
                if (line.trim().startsWith("*/")) {
                    inJavadoc = false
                }
                if (line.contains(anchor)) {
                    return new Lookup(true, javadoc)
                }
            }
            return new Lookup(false, [])
        }

        boolean javadocContains(String text) {
            javadoc.any { it.contains(text) }
        }
    }

    @Canonical
    static class TestClassSource implements ClassSource {

        final String className
        final String classSource

        @Override
        String getPackageName() {
            'org.test'
        }

        @Override
        String getSimpleClassName() {
            className
        }

        @Override
        String getSource() {
            classSource
        }
    }
}
