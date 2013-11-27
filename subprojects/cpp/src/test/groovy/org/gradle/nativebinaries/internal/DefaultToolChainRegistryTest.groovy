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

package org.gradle.nativebinaries.internal

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultToolChainRegistryTest extends Specification {
    def project = TestUtil.createRootProject()
    def instantiator = project.services.get(Instantiator)
    def registry = instantiator.newInstance(DefaultToolChainRegistry, instantiator)
    def NamedDomainObjectFactory<TestToolChain> factory = Mock(NamedDomainObjectFactory)
    def platform = new DefaultPlatform("platform")

    def "setup"() {
        project.extensions.add("toolChains", registry)
        registry.registerFactory(TestToolChain, factory)
    }

    def "adds default tool chains"() {
        def defaultToolChain1 = unavailableToolChain("test1")
        def defaultToolChain2 = availableToolChain("test2")
        def defaultToolChain3 = availableToolChain("test3")

        when:
        registry.registerDefaultToolChain("test1", TestToolChain)
        registry.registerDefaultToolChain("test2", TestToolChain)
        registry.registerDefaultToolChain("test3", TestToolChain)
        registry.addDefaultToolChains()

        then:
        registry.asList() == [defaultToolChain1, defaultToolChain2, defaultToolChain3]
    }

    def "can add default tool chain when some configured"() {
        def defaultToolChain = availableToolChain("default")
        def configuredToolChain = unavailableToolChain("configured")

        when:
        registry.registerDefaultToolChain("default", TestToolChain)

        and:
        registry.create("configured", TestToolChain)

        and:
        registry.addDefaultToolChains()

        then:
        registry.asList() == [configuredToolChain, defaultToolChain]
    }

    def "provides unavailable tool chain when no tool chain available"() {
        unavailableToolChain("test", "nope")
        unavailableToolChain("test2", "not me")
        unavailableToolChain("test3", "not me either")

        when:
        registry.registerDefaultToolChain("test", TestToolChain)
        registry.registerDefaultToolChain("test2", TestToolChain)
        registry.registerDefaultToolChain("test3", TestToolChain)
        registry.addDefaultToolChains()

        then:
        registry.size() == 3

        when:
        def tc = registry.getForPlatform(platform)
        tc.target(platform)

        then:
        GradleException e = thrown()
        e.message == "No tool chain is available: [Could not load 'test': nope, Could not load 'test2': not me, Could not load 'test3': not me either]"
    }

    def "can use DSL to configure toolchains"() {
        def defaultToolChain = availableToolChain("test")
        def anotherToolChain = unavailableToolChain("another")

        when:
        registry.registerDefaultToolChain("test", TestToolChain)
        registry.addDefaultToolChains()

        and:
        project.toolChains {
            test {
                baseDir = "foo"
            }
            another(TestToolChain) {
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
        registry.create("test", TestToolChain)
        registry.create("test2", TestToolChain)
        registry.create("first", TestToolChain)

        then:
        registry.toList() == [tcFirst, test, tc2]
    }

    def "uses first available tool chain that can target platform"() {
        def defaultToolChain1 = unavailableToolChain("test1")
        def defaultToolChain2 = availableToolChain("test2")
        def defaultToolChain3 = availableToolChain("test3")

        when:
        registry.add(defaultToolChain1)
        registry.add(defaultToolChain2)
        registry.add(defaultToolChain3)

        defaultToolChain1.canTargetPlatform(platform) >> true
        defaultToolChain2.canTargetPlatform(platform) >> false
        defaultToolChain3.canTargetPlatform(platform) >> true

        then:
        registry.getForPlatform(platform) == defaultToolChain3
    }

    def "tool chains are inspected in order added"() {
        def defaultToolChain1 = availableToolChain("test1")
        def defaultToolChain2 = availableToolChain("first")

        when:
        registry.add(defaultToolChain1)
        registry.add(defaultToolChain2)

        defaultToolChain1.canTargetPlatform(platform) >> true
        defaultToolChain2.canTargetPlatform(platform) >> true

        then:
        registry.getForPlatform(platform) == defaultToolChain1
    }

    def availableToolChain(String name) {
        TestToolChain testToolChain = Mock(TestToolChain) {
            _ * getName() >> name
            _ * getAvailability() >> new ToolChainAvailability()
        }
        factory.create(name) >> testToolChain
        return testToolChain
    }

    def unavailableToolChain(String name, String message = "Not available") {
        TestToolChain testToolChain = Mock(TestToolChain) {
            _ * getName() >> name
            _ * getAvailability() >> new ToolChainAvailability().unavailable(message)
        }
        factory.create(name) >> testToolChain
        return testToolChain
    }

    interface TestToolChain extends ToolChainInternal
    {
        void setBaseDir(String value);
    }

}
