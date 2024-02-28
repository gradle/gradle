/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.jvm

import org.gradle.StartParameter
import org.gradle.api.internal.provider.Providers
import spock.lang.Specification
import static org.junit.Assert.assertEquals

class DaemonProviderFactoryTest extends Specification {

    def "prioritise project properties over system properties"() {
        given:
        def startParameter = new StartParameter()
        startParameter.getProjectProperties().putAll([
            "testProperty1": "project-value1",
            "testProperty2": "project-value2",
            "testProperty3": "project-value3"
        ])
        startParameter.getSystemPropertiesArgs().putAll([
            "testProperty1": "system-value1",
            "testProperty2": "system-value2",
            "testProperty4": "system-value4"
        ])

        when:
        def provider = new DaemonProviderFactory(startParameter)

        then:
        assertProviderProperties(provider, [
            "testProperty1": "project-value1",
            "testProperty3": "project-value3",
            "testProperty4": "system-value4",
        ])
    }

    def "allow to override existing properties or add it if missing"() {
        given:
        def startParameter = new StartParameter()
        startParameter.getProjectProperties().putAll([
            "property1": "false",
            "property2": "false"
        ])
        startParameter.getSystemPropertiesArgs().putAll([
            "property1": "false",
            "property2": "false"
        ])

        when:
        def provider = new DaemonProviderFactory(startParameter)
        provider.overrideProperty("property1", true)
        provider.overrideProperty("property3", false)
        provider.overrideProperty("property4", true)

        then:
        assertProviderProperties(provider, [
            "property1": "true",
            "property2": "false",
            "property3": "false",
            "property4": "true",
        ])
    }

    def assertProviderProperties(DaemonProviderFactory provider, Map<String, String> expectedPropertiesMap) {
        expectedPropertiesMap.each { key, value ->
            assertEquals(value, provider.gradleProperty(key).get())
            assertEquals(value, provider.gradleProperty(Providers.of(key)).get())
            assertEquals(value, provider.systemProperty(key).get())
            assertEquals(value, provider.systemProperty(Providers.of(key)).get())
        }
    }
}
