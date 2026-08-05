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

package org.gradle.internal.cc.impl

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

    def "entry is not reused when the build directory is copied to a new location"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile 'original/settings.gradle', """rootProject.name = 'relocated-build'"""
        buildFile 'original/build.gradle', """task ok"""

        when:
        inDirectory 'original'
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateStored()

        when: 'running again in the original location'
        inDirectory 'original'
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateLoaded()

        when: 'the build directory is copied, configuration cache included, and the original is kept'
        file('original').copyTo(file('copy'))
        inDirectory 'copy'
        configurationCacheRun 'ok'

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because the location of the build has changed from '${file('original')}' to '${file('copy')}'.")
        configurationCache.assertStateStored()

        when: 'running again in the new location'
        inDirectory 'copy'
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateLoaded()
    }

    def "entry is not reused when the build directory is moved to a new location"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile 'original/settings.gradle', """rootProject.name = 'relocated-build'"""
        buildFile 'original/build.gradle', """task ok"""

        when:
        inDirectory 'original'
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateStored()

        when: 'the build directory is moved, configuration cache included'
        file('original').renameTo(file('moved'))
        inDirectory 'moved'
        configurationCacheRun 'ok'

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because the location of the build has changed from '${file('original')}' to '${file('moved')}'.")
        configurationCache.assertStateStored()

        when: 'running again in the new location'
        inDirectory 'moved'
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateLoaded()
    }
}
