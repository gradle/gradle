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

class ConfigurationCacheMultiEntriesPerKeyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def 'store single entry per key by default'() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        and:
        settingsFile.text = '// original branch'

        when:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateStored()

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateStored()

        when: 'switching back to original branch'
        settingsFile.text = '// original branch'

        then:
        configurationCache.assertStateStored()
    }

    def 'can store multiple entries per key'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        file("gradle.properties") << "org.gradle.configuration-cache.entries-per-key=2"

        and:
        settingsFile.text = '// original branch'

        when:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateStored()

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateStored()

        when: 'switching back to original branch'
        settingsFile.text = '// original branch'

        and:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateLoaded()

        when:
        settingsFile.text = '// second branch'

        and:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateLoaded()

        when:
        settingsFile.text = '// third branch'

        and:
        configurationCacheRun 'help'

        then:
        configurationCache.assertStateStored()

        when: 'switching back to original branch'
        settingsFile.text = '// original branch'

        and:
        configurationCacheRun 'help'

        then: 'least recently used entries are evicted'
        configurationCache.assertStateStored()
    }
}
