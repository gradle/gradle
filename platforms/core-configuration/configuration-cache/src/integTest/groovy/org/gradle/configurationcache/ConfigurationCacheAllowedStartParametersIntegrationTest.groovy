/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheAllowedStartParametersIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "changing of kotlin dsl cid property doesn't invalidates cache entry"() {
        given:
        def kotlinDslCidProp = "org.gradle.kotlin.dsl.provider.cid"
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "tasks", "-P$kotlinDslCidProp=24"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "tasks", "-P$kotlinDslCidProp=42"

        then:
        configurationCache.assertStateLoaded()
    }
}
