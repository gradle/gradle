/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.capabilities

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

class CapabilitiesExtensionTest extends Specification {
    ConfigurationContainer configurations = Mock(ConfigurationContainer)

    @Subject
    CapabilitiesExtension capabilities = TestUtil.instantiatorFactory().decorate().newInstance(CapabilitiesExtension, configurations)

    def "can declare capabilities"() {
        def conf1 = Stub(Configuration)
        def conf2 = Stub(Configuration)
        configurations.getByName('conf') >> conf1
        configurations.getByName('conf2') >> conf2
        configurations.contains(_) >> { args ->
            args[0] in [conf1, conf2]
        }

        when:
        capabilities.add('conf', 'org', 'foo', '1.0')
        def caps = capabilities.getCapabilities(conf1)

        then:
        caps.size() == 1
        caps[0].group == 'org'
        caps[0].name == 'foo'
        caps[0].version == '1.0'

        when:
        capabilities.add(conf1, 'org', 'bar', '1.0')
        caps = capabilities.getCapabilities(conf1)

        then:
        caps.size() == 2
        caps[1].group == 'org'
        caps[1].name == 'bar'
        caps[1].version == '1.0'

        when:
        capabilities.add(conf2, 'org', 'baz', '1.0')
        caps = capabilities.getCapabilities(conf2)

        then:
        caps.size() == 1
        caps[0].group == 'org'
        caps[0].name == 'baz'
        caps[0].version == '1.0'

    }

    def "cannot declare capabilities for a foreign configuration"() {
        configurations.contains(_) >> false

        when:
        capabilities.add(Stub(Configuration), 'org', 'foo', '1.0')

        then:
        InvalidUserDataException ex = thrown()
        ex.message == 'Currently you can only declare capabilities on configurations from the same project.'
    }

    def "cannot declare the same capability with 2 different versions"() {
        configurations.getByName('conf') >> Stub(Configuration)
        configurations.contains(_) >> true

        when:
        capabilities.add('conf', 'org', 'foo', '1.0')
        capabilities.add('conf', 'org', 'foo', '1.1')

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Cannot add capability org:foo with version 1.1 because it's already defined with version 1.0"
    }

    def "can add a capability using DSL notation"() {
        def conf = Stub(Configuration)
        configurations.findByName('conf') >> conf
        configurations.getByName('conf') >> conf
        configurations.contains(_) >> true

        when:
        capabilities.conf('org:foo:1.0')
        def caps = capabilities.getCapabilities(conf)

        then:
        caps.size() == 1
        caps[0].group == 'org'
        caps[0].name == 'foo'
        caps[0].version == '1.0'
    }

    def "validates short-hand notation for capability"() {
        def conf = Stub(Configuration)
        configurations.findByName('conf') >> conf
        configurations.getByName('conf') >> conf
        configurations.contains(_) >> true

        when:
        capabilities.conf('blah')

        then:
        InvalidUserDataException ex = thrown()
        ex.message == "Invalid capability notation: 'blah'. Capabilities notation consists of group, name, version separated by semicolons. For example: 'org.mycompany:capability:1.0'."
    }
}
