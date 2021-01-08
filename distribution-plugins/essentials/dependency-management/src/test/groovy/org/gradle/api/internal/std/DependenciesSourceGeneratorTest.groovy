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
import groovy.transform.Canonical
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.internal.management.VersionCatalogBuilderInternal
import org.gradle.internal.service.scopes.Scopes
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Supplier

import static org.gradle.api.internal.std.AbstractSourceGenerator.toJavaName
import static org.gradle.util.TextUtil.normaliseLineSeparators

class DependenciesSourceGeneratorTest extends Specification {

    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get())
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))
    private final ClassPath classPath = classPathRegistry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER")

    @Rule
    private final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    private GeneratedSource sources
    final ProviderFactory providerFactory = new DefaultProviderFactory(
        new DefaultValueSourceProviderFactory(
            Stub(ConfigurationTimeBarrier),
            new DefaultListenerManager(Scopes.Build),
            TestUtil.instantiatorFactory(),
            new TestIsolatableFactory(),
            Stub(GradleProperties),
            TestUtil.services()
        ),
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

    @Unroll
    def "generates an accessor for #name as method #method"() {
        when:
        generate {
            alias(name).to 'g:a:v'
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

    @Unroll
    def "generates an accessor for bundle #name as method #method"() {
        when:
        generate {
            alias('foo') to 'g:a:v'
            alias('bar') to 'g:a:v'
            bundle(name, ['foo', 'bar'])
        }

        then:
        sources.hasBundle(name, method)

        where:
        name          | method
        'test'        | 'getTest'
        'testBundle'  | 'getTestBundle'
        'test.bundle' | 'getTestBundle'
        'test.json'   | 'getTestJson'
        'a.b'         | 'getAB'
    }

    @Unroll
    def "generates an accessor for #name as version #method"() {
        when:
        generate {
            version(name, '1.0')
        }

        then:
        sources.hasVersion(name, method)

        where:
        name             | method
        'groovy'         | 'getGroovy'
        'groovyVersion'  | 'getGroovyVersion'
        'groovy.version' | 'getGroovyVersion'
        'groovy-json'    | 'getGroovyJson'
        'groovy.json'    | 'getGroovyJson'
        'groovyJson'     | 'getGroovyJson'
        'lang3Version'   | 'getLang3Version'
    }

    def "reasonable error message if methods have the same name"() {
        when:
        generate {
            alias('groovy.json') to 'g:a:v'
            alias('groovyJson') to 'g:a:v'
        }

        then:
        InvalidUserDataException ex = thrown()
        ex.message == 'Cannot generate dependency accessors because dependency aliases groovy.json and groovyJson are mapped to the same accessor name getGroovyJson()'

        when:
        generate {
            alias('groovy.json') to 'g:a:v'
            alias('groovyJson') to 'g:a:v'

            alias('tada_one') to 'g:a:v'
            alias('tada.one') to 'g:a:v'
            alias('tadaOne') to 'g:a:v'
        }

        then:
        ex = thrown()
        normaliseLineSeparators(ex.message) == '''Cannot generate dependency accessors because:
  - dependency aliases groovy.json and groovyJson are mapped to the same accessor name getGroovyJson()
  - dependency aliases tada.one and tadaOne and tada_one are mapped to the same accessor name getTadaOne()'''
    }

    def "reasonable error message if bundles have the same name"() {
        when:
        generate {
            alias('foo') to 'g:a:v'
            alias('bar') to 'g:a:v'
            bundle('one.cool', ['foo', 'bar'])
            bundle('oneCool', ['foo', 'bar'])
        }

        then:
        InvalidUserDataException ex = thrown()
        normaliseLineSeparators(ex.message) == 'Cannot generate dependency accessors because dependency bundles one.cool and oneCool are mapped to the same accessor name getOneCoolBundle()'

        when:
        generate {
            alias('foo') to 'g:a:v'
            alias('bar') to 'g:a:v'
            bundle('one.cool', ['foo', 'bar'])
            bundle('oneCool', ['foo', 'bar'])

            bundle("other.cool", ['foo'])
            bundle("other_cool", ['bar'])
            bundle("otherCool", ['bar'])
        }

        then:
        ex = thrown()
        normaliseLineSeparators(ex.message) == '''Cannot generate dependency accessors because:
  - dependency bundles one.cool and oneCool are mapped to the same accessor name getOneCoolBundle()
  - dependency bundles other.cool and otherCool and other_cool are mapped to the same accessor name getOtherCoolBundle()'''
    }

    def "generated sources can be compiled"() {
        when:
        generate {
            alias('foo') to 'g:a:v'
            alias('bar') to 'g2:a2:v2'
            bundle('myBundle', ['foo', 'bar'])
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
    }

    def "puts limit on the number of methods"() {
        when:
        generate {
            16000.times { n ->
                alias("alias$n").to("g:a$n:1.0")
                bundle("foo$n", ["alias$n".toString()])
            }
        }

        then:
        InvalidUserDataException ex = thrown()
        ex.message == 'Cannot generate dependency accessors because model contains too many entries (32000), maximum is 30000'
    }

    def "outputs context in javadocs"() {
        def context = "some plugin"
        def innerContext = "some inner plugin"
        when:
        generate {
            description.set("Some description for tests")
            withContext(context) {
                alias("some-alias").to 'g:a:v'
                bundle("b0Bundle", ["some-alias"])
                withContext(innerContext) {
                    version("v0Version", "1.0")
                }
                alias("other").to("g", "a").versionRef("v0Version")
            }
        }

        then:
        sources.assertClass('Generated', 'Some description for tests')
        sources.hasDependencyAlias('some-alias', 'getAlias', "This dependency was declared in ${context}")
        sources.hasBundle('b0Bundle', 'getB0Bundle', "This bundle was declared in ${context}")
        sources.hasVersion('v0Version', 'getV0Version', "This version was declared in ${innerContext}")
        sources.hasDependencyAlias('other', 'getOther', "This dependency was declared in ${context}")
    }

    private void generate(String className = 'Generated', @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = VersionCatalogBuilderInternal) Closure<Void> spec) {
        DefaultVersionCatalogBuilder builder = new DefaultVersionCatalogBuilder("lib", Interners.newStrongInterner(), Interners.newStrongInterner(), TestUtil.objectFactory(), TestUtil.providerFactory(), Stub(PluginDependenciesSpec), Stub(Supplier))
        spec.delegate = builder
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        def writer = new StringWriter()
        def model = builder.build()
        DependenciesSourceGenerator.generateSource(writer, model, 'org.test', className)
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

        void hasDependencyAlias(String name, String methodName = "get${toJavaName(name)}", String javadoc = null) {
            def lookup = "public Provider<MinimalExternalModuleDependency> $methodName() { return create(\"$name\"); }"
            def result = Lookup.find(lines, lookup)
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        void hasBundle(String name, String methodName = "get${toJavaName(name)}Bundle", String javadoc = null) {
            def lookup = "public Provider<ExternalModuleDependencyBundle> $methodName() { return createBundle(\"$name\"); }"
            def result = Lookup.find(lines, lookup)
            assert result.match
            if (javadoc) {
                assert result.javadocContains(javadoc)
            }
        }

        void hasVersion(String name, String methodName = "get${toJavaName(name)}Version", String javadoc = null) {
            def lookup = "public Provider<String> $methodName() { return getVersion(\"$name\"); }"
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
            factory.newInstance(model, providerFactory)
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
