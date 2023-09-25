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

package org.gradle.configurationcache

class ConfigurationCacheDirIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "configuration cache dir is not created unless needed"() {
        when:
        run 'help'

        then:
        !file('.gradle/configuration-cache').exists()

        when:
        configurationCacheRun 'help'

        then:
        file('.gradle/configuration-cache').isDirectory()
    }

    def "configuration cache honours --project-cache-dir"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'help', '--project-cache-dir', 'custom-cache-dir'

        then:
        !file('.gradle/configuration-cache').exists()

        and:
        file('custom-cache-dir/configuration-cache').isDirectory()

        and:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'help', '--project-cache-dir', 'custom-cache-dir'

        then:
        configurationCache.assertStateLoaded()
    }

    def "configuration cache honours org.gradle.projectcachedir"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'help', '-Dorg.gradle.projectcachedir=custom-cache-dir'

        then:
        !file('.gradle/configuration-cache').exists()

        and:
        file('custom-cache-dir/configuration-cache').isDirectory()

        and:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'help', '-Dorg.gradle.projectcachedir=custom-cache-dir'

        then:
        configurationCache.assertStateLoaded()
    }
}
