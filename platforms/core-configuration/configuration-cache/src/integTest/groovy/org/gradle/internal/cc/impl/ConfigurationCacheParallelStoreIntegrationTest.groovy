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

package org.gradle.internal.cc.impl

class ConfigurationCacheParallelStoreIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "parallel store is enabled by default"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d")

        then:
        output.contains("[org.gradle.configurationcache] saving state nodes in parallel")
        output.contains("[org.gradle.configurationcache] reading state nodes in parallel")
    }

    def "parallel store may be opted out"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d", "-Dorg.gradle.configuration-cache.internal.parallel-store=false")

        then:
        output.contains("[org.gradle.configurationcache] saving state nodes in-line")
        // reading is always in parallel
        output.contains("[org.gradle.configurationcache] reading state nodes in parallel")
    }

    def "parallel load may be opted out"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d", "-Dorg.gradle.configuration-cache.internal.parallel-load=false")

        then:
        output.contains("[org.gradle.configurationcache] saving state nodes in parallel")
        // reading is always in parallel
        output.contains("[org.gradle.configurationcache] reading state nodes in-line")
    }
}
