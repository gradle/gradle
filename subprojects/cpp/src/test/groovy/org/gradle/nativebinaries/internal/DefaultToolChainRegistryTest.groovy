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
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativebinaries.Platform
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultToolChainRegistryTest extends Specification {
    def project = TestUtil.createRootProject()
    def instantiator = project.services.get(Instantiator)
    def registry = instantiator.newInstance(DefaultToolChainRegistry, instantiator)
    def NamedDomainObjectFactory<TestToolChain> factory = Mock(NamedDomainObjectFactory)

    def "setup"() {
        project.extensions.add("toolChains", registry)
        registry.registerFactory(TestToolChain, factory)
    }

    def "adds first available default tool chain when none configured"() {
        unavailableToolChain("test1")
        def defaultToolChain2 = availableToolChain("test2")

        when:
        registry.registerDefaultToolChain("test1", TestToolChain)
        registry.registerDefaultToolChain("test2", TestToolChain)
        registry.registerDefaultToolChain("test3", TestToolChain)
        registry.addDefaultToolChain()

        then:
        registry.asList() == [defaultToolChain2]
        registry.availableToolChains == [defaultToolChain2]
    }

    def "can add default tool chain when some configured"() {
        def defaultToolChain = availableToolChain("default")
        def configuredToolChain = unavailableToolChain("configured")

        when:
        registry.registerDefaultToolChain("default", TestToolChain)

        and:
        registry.create("configured", TestToolChain)

        and:
        registry.addDefaultToolChain()

        then:
        registry.asList() == [configuredToolChain, defaultToolChain]
    }

    def "can use DSL to configure toolchains"() {
        def defaultToolChain = availableToolChain("test")
        def anotherToolChain = unavailableToolChain("another")

        when:
        registry.registerDefaultToolChain("test", TestToolChain)
        registry.addDefaultToolChain()

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

    def "returns all available toolchains from configured in name order"() {
        unavailableToolChain("test")
        def tc2 = availableToolChain("test2")
        def tcFirst = availableToolChain("first")

        when:
        registry.create("test", TestToolChain)
        registry.create("test2", TestToolChain)
        registry.create("first", TestToolChain)

        then:
        registry.availableToolChains == [tcFirst, tc2]
    }

    def "reports unavailability when no tool chain available"() {
        unavailableToolChain("test", "nope")
        unavailableToolChain("test2", "not me")
        unavailableToolChain("test3", "not me either")

        given:
        registry.create("test", TestToolChain)
        registry.create("test2", TestToolChain)
        registry.create("test3", TestToolChain)

        when:
        def defaultToolChain = registry.defaultToolChain
        defaultToolChain.target(Stub(Platform))

        then:
        IllegalStateException e = thrown()
        e.message == "No tool chain is available: [Could not load 'test': nope, Could not load 'test2': not me, Could not load 'test3': not me either]"
    }

    def availableToolChain(String name) {
        TestToolChain testToolChain = Mock(TestToolChain) {
            _ * getName() >> name
            _ * getAvailability() >> new ToolChainAvailability()
        }
        1 * factory.create(name) >> testToolChain
        return testToolChain
    }

    def unavailableToolChain(String name, String message = "Not available") {
        TestToolChain testToolChain = Mock(TestToolChain) {
            _ * getName() >> name
            _ * getAvailability() >> new ToolChainAvailability().unavailable(message)
        }
        1 * factory.create(name) >> testToolChain
        return testToolChain
    }

    interface TestToolChain extends ToolChainInternal
    {
        void setBaseDir(String value);
    }

}
