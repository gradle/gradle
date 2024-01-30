/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DefaultNativeToolChainRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())

    def project = TestUtil.create(testDir).rootProject()

    def instantiator = project.services.get(Instantiator)
    def registry = instantiator.newInstance(DefaultNativeToolChainRegistry, instantiator, CollectionCallbackActionDecorator.NOOP)
    def factory = Mock(NamedDomainObjectFactory)
    def platform = new DefaultNativePlatform("platform")

    def "setup"() {
        project.extensions.add("toolChains", registry)
        registry.registerFactory(TestNativeToolChain, factory)
    }

    def "adds default tool chains"() {
        def defaultToolChain1 = unavailableToolChain("test1")
        def defaultToolChain2 = availableToolChain("test2")
        def defaultToolChain3 = availableToolChain("test3")

        when:
        registry.registerDefaultToolChain("test1", TestNativeToolChain)
        registry.registerDefaultToolChain("test2", TestNativeToolChain)
        registry.registerDefaultToolChain("test3", TestNativeToolChain)
        registry.addDefaultToolChains()

        then:
        registry.asList() == [defaultToolChain1, defaultToolChain2, defaultToolChain3]
    }

    def "can add default tool chain when some configured"() {
        def defaultToolChain = availableToolChain("default")
        def configuredToolChain = unavailableToolChain("configured")

        when:
        registry.registerDefaultToolChain("default", TestNativeToolChain)

        and:
        registry.create("configured", TestNativeToolChain)

        and:
        registry.addDefaultToolChains()

        then:
        registry.asList() == [configuredToolChain, defaultToolChain]
    }

    def "provides unavailable tool chain when no tool chain available for requested platform"() {
        unavailableToolChain("test", "nope")
        unavailableToolChain("test2", "not me")
        unavailableToolChain("test3", "not me either")

        given:
        registry.registerDefaultToolChain("test", TestNativeToolChain)
        registry.registerDefaultToolChain("test2", TestNativeToolChain)
        registry.registerDefaultToolChain("test3", TestNativeToolChain)
        registry.addDefaultToolChains()

        and:
        def tc = registry.getForPlatform(platform)
        def result = tc.select(platform)

        when:
        result.newCompiler(CCompileSpec.class)

        then:
        GradleException e = thrown()
        e.message == toPlatformLineSeparators("""No tool chain is available to build for platform 'platform':
  - Tool chain 'test': nope
  - Tool chain 'test2': not me
  - Tool chain 'test3': not me either""")
    }

    def "provides unavailable tool chain when no tool chain available for requested source language and target platform"() {
        unavailableToolChain("test", "nope")
        unavailableToolChain("test2", "not me")
        unavailableToolChain("test3", "not me either")

        given:
        registry.registerDefaultToolChain("test", TestNativeToolChain)
        registry.registerDefaultToolChain("test2", TestNativeToolChain)
        registry.registerDefaultToolChain("test3", TestNativeToolChain)
        registry.addDefaultToolChains()

        and:
        def tc = registry.getForPlatform(NativeLanguage.CPP, platform)
        def result = tc.select(platform)

        when:
        result.newCompiler(CCompileSpec.class)

        then:
        GradleException e = thrown()
        e.message == toPlatformLineSeparators("""No tool chain has support to build C++ for platform 'platform':
  - Tool chain 'test': nope
  - Tool chain 'test2': not me
  - Tool chain 'test3': not me either""")
    }

    def "can use DSL to configure toolchains"() {
        def defaultToolChain = availableToolChain("test")
        def anotherToolChain = unavailableToolChain("another")

        when:
        registry.registerDefaultToolChain("test", TestNativeToolChain)
        registry.addDefaultToolChains()

        and:
        project.toolChains {
            test {
                baseDir = "foo"
            }
            another(TestNativeToolChain) {
                baseDir = "bar"
            }
        }

        then:
        1 * defaultToolChain.setBaseDir("foo")
        1 * anotherToolChain.setBaseDir("bar")

        and:
        registry.asList() == [anotherToolChain, defaultToolChain]
    }

    def "tool chains are returned in name order"() {
        def test = unavailableToolChain("test")
        def tc2 = availableToolChain("test2")
        def tcFirst = availableToolChain("first")

        when:
        registry.create("test", TestNativeToolChain)
        registry.create("test2", TestNativeToolChain)
        registry.create("first", TestNativeToolChain)

        then:
        registry.toList() == [tcFirst, test, tc2]
    }

    def "uses first available tool chain that can target platform"() {
        def defaultToolChain1 = unavailableToolChain("test1")
        def defaultToolChain2 = availableToolChain("test2")
        def defaultToolChain3 = availableToolChain("test3")

        given:
        registry.add(defaultToolChain1)
        registry.add(defaultToolChain2)
        registry.add(defaultToolChain3)

        expect:
        registry.getForPlatform(platform) == defaultToolChain2
    }

    def availableToolChain(String name) {
        PlatformToolProvider platformToolChain = Stub(PlatformToolProvider) {
            _ * isAvailable() >> true
        }
        TestNativeToolChain testToolChain = Mock(TestNativeToolChain) {
            _ * getName() >> name
            _ * select(_, platform) >> platformToolChain
        }
        factory.create(name) >> testToolChain
        return testToolChain
    }

    def unavailableToolChain(String name, String message = "Not available") {
        PlatformToolProvider platformToolChain = Stub(PlatformToolProvider) {
            _ * isAvailable() >> false
            _ * explain(_) >> { it[0].node(message) }
        }
        TestNativeToolChain testToolChain = Mock(TestNativeToolChain) {
            _ * getName() >> name
            _ * getDisplayName() >> "Tool chain '$name'"
            _ * select(_, platform) >> platformToolChain
        }
        factory.create(name) >> testToolChain
        return testToolChain
    }

    interface TestNativeToolChain extends NativeToolChainInternal
    {
        void setBaseDir(String value);
    }

}
