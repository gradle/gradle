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

package org.gradle.nativecode.base.internal
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.HelperUtil
import spock.lang.Specification

class DefaultToolChainRegistryTest extends Specification {
    def project = HelperUtil.createRootProject()
    def instantiator = project.services.get(Instantiator)
    def registry = instantiator.newInstance(DefaultToolChainRegistry, instantiator)
    def NamedDomainObjectFactory<TestToolChain> factory = Mock()

    def "setup"() {
        project.extensions.add("toolChains", registry)
        registry.registerFactory(TestToolChain, factory)
    }

    def "has default toolchains when none configured"() {
        def defaultToolChain1 = testToolChain("test")
        def defaultToolChain2 = testToolChain("test2")

        when:
        registry.registerDefaultToolChain("test", TestToolChain)
        registry.registerDefaultToolChain("test2", TestToolChain)

        then:
        registry.asList() == [defaultToolChain1, defaultToolChain2]
    }

    def "explicitly created toolchain overwrites default toolchain"() {
        testToolChain("default")
        def configuredToolChain = testToolChain("configured")

        when:
        registry.registerDefaultToolChain("default", TestToolChain)

        and:
        registry.create("configured", TestToolChain)

        then:
        registry.asList() == [configuredToolChain]
    }

    def "explicitly added toolchain overwrites default toolchain"() {
        testToolChain("default")
        def addedToolChain = Stub(TestToolChain) {
            getName() >> "added"
        }

        when:
        registry.registerDefaultToolChain("default", TestToolChain)

        and:
        registry.add(addedToolChain)

        then:
        registry.asList() == [addedToolChain]
    }

    def "can use DSL replace and add to default toolchain list"() {
        def defaultToolChain = testToolChain("test")
        def replacementToolChain = testToolChain("test")
        def anotherToolChain = testToolChain("another")

        when:
        registry.registerDefaultToolChain("test", TestToolChain)

        and:
        project.toolChains {
            test(TestToolChain) {
                baseDir = "foo"
            }
            another(TestToolChain) {
                baseDir = "bar"
            }
        }

        then:
        1 * replacementToolChain.setBaseDir("foo")
        1 * anotherToolChain.setBaseDir("bar")

        and:
        registry.asList() == [anotherToolChain, replacementToolChain]
    }

    def "returns all available toolchains from defaults in order added"() {
        def tc1 = testToolChain("test")
        def tc2 = testToolChain("test2")
        def tc3 = testToolChain("test3")

        when:
        registry.registerDefaultToolChain("test", TestToolChain)
        registry.registerDefaultToolChain("test2", TestToolChain)
        registry.registerDefaultToolChain("test3", TestToolChain)

        and:
        tc1.getAvailability() >> new ToolChainAvailability()
        tc2.getAvailability() >> new ToolChainAvailability().unavailable("not available")
        tc3.getAvailability() >> new ToolChainAvailability()

        then:
        registry.availableToolChains == [tc1, tc3]
    }

    def "returns all available toolchains from configured in order added"() {
        def tc1 = testToolChain("test")
        def tc2 = testToolChain("test2")
        def tcLast = testToolChain("last")

        when:
        registry.create("test", TestToolChain)
        registry.create("test2", TestToolChain)
        registry.create("last", TestToolChain)

        and:
        tc1.getAvailability() >> new ToolChainAvailability()
        tc2.getAvailability() >> new ToolChainAvailability().unavailable("not available")
        tcLast.getAvailability() >> new ToolChainAvailability()

        then:
        registry.availableToolChains == [tc1, tcLast]
    }

    def "default toolchain is first available"() {
        def tc1 = testToolChain("test")
        def tc2 = testToolChain("test2")
        def tcLast = testToolChain("last")

        when:
        registry.create("test", TestToolChain)
        registry.create("test2", TestToolChain)
        registry.create("last", TestToolChain)

        and:
        tc1.getAvailability() >> new ToolChainAvailability().unavailable("not available")
        tc2.getAvailability() >> new ToolChainAvailability()
        tcLast.getAvailability() >> new ToolChainAvailability()

        then:
        registry.defaultToolChain == tc2
    }

    def "reports unavailability when no tool chain available"() {
        def tc1 = testToolChain("test")
        def tc2 = testToolChain("test2")
        def tc3 = testToolChain("last")

        given:
        registry.create("test", TestToolChain)
        registry.create("test2", TestToolChain)
        registry.create("last", TestToolChain)

        and:
        tc1.availability >> new ToolChainAvailability().unavailable("nope")
        tc2.availability >> new ToolChainAvailability().unavailable("not me")
        tc3.availability >> new ToolChainAvailability().unavailable("not me either")

        when:
        def defaultToolChain = registry.defaultToolChain
        defaultToolChain.createCCompiler()

        then:
        IllegalStateException e = thrown()
        e.message == "No tool chain is available: [Could not load 'test': nope, Could not load 'test2': not me, Could not load 'last': not me either]"
    }

    def testToolChain(String name) {
        TestToolChain testToolChain = Mock()
        _ * testToolChain.name >> name
        1 * factory.create(name) >> testToolChain
        return testToolChain
    }

    interface TestToolChain extends ToolChainInternal
    {
        void setBaseDir(String value);
    }

}
