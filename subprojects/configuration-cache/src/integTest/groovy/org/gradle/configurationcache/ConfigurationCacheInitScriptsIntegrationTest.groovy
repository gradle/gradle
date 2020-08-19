/*
 * Copyright 2020 the original author or authors.
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

class ConfigurationCacheInitScriptsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def "init script names do not matter, their contents do"() {

        given:
        def initScript1 = file('initscript1.gradle.kts').tap {
            text = 'println("initscript1!")'
        }
        def initScript2 = file('initscript2.gradle').tap {
            text = 'println("initscript2!")'
        }
        buildFile << '''
            task build
        '''

        when:
        configurationCacheRun 'build', '-I', initScript1.absolutePath, '-I', initScript2.absolutePath

        then:
        outputContains 'initscript1!'
        outputContains 'initscript2!'
        configurationCache.assertStateStored()

        when:
        def newInitScript1 = file('new' + initScript1.name)
        def newInitScript2 = file('new' + initScript2.name)
        initScript1.renameTo(newInitScript1)
        initScript2.renameTo(newInitScript2)
        configurationCacheRun 'build', '-I', newInitScript1.absolutePath, '-I', newInitScript2.absolutePath

        then:
        outputDoesNotContain 'initscript1!'
        outputDoesNotContain 'initscript2!'
        configurationCache.assertStateLoaded()
    }

    def "invalidates cache upon changes to init script content order"() {

        given:
        def initScript1 = file('initscript1.gradle.kts').tap {
            text = 'println("initscript1!")'
        }
        def initScript2 = file('initscript2.gradle').tap {
            text = 'println("initscript2!")'
        }
        buildFile << '''
            task build
        '''

        when:
        configurationCacheRun 'build', '-I', initScript1.absolutePath, '-I', initScript2.absolutePath

        then:
        output.indexOf('initscript1!') < output.indexOf('initscript2!')
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'build', '-I', initScript2.absolutePath, '-I', initScript1.absolutePath

        then:
        output.indexOf('initscript2!') < output.indexOf('initscript1!')
        configurationCache.assertStateStored()
    }

    def "invalidates cache upon adding init script to command line"() {

        given:
        def initScript1 = file('initscript1.gradle.kts').tap {
            text = 'println("initscript1!")'
        }
        def initScript2 = file('initscript2.gradle').tap {
            text = 'println("initscript2!")'
        }
        buildFile << '''
            task build
        '''

        when:
        configurationCacheRun 'build', '-I', initScript1.absolutePath

        then:
        outputContains 'initscript1!'
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'build', '-I', initScript1.absolutePath, '-I', initScript2.absolutePath

        then:
        output.indexOf('initscript1!') < output.indexOf('initscript2!')
        configurationCache.assertStateStored()
    }

    def "invalidates cache upon removing init script from command line"() {

        given:
        def initScript1 = file('initscript1.gradle.kts').tap {
            text = 'println("initscript1!")'
        }
        def initScript2 = file('initscript2.gradle').tap {
            text = 'println("initscript2!")'
        }
        buildFile << '''
            task build
        '''

        when:
        configurationCacheRun 'build', '-I', initScript1.absolutePath, '-I', initScript2.absolutePath

        then:
        output.indexOf('initscript1!') < output.indexOf('initscript2!')
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'build', '-I', initScript1.absolutePath

        then:
        outputContains 'initscript1!'
        outputDoesNotContain 'initscript2!'
        configurationCache.assertStateStored()
    }

    def "invalidates cache upon adding init script to Gradle home"() {

        requireOwnGradleUserHomeDir()

        given:
        gradleUserHomeDirFile('init.d/initscript1.gradle.kts').tap {
            text = 'println("initscript1!")'
        }
        buildFile << '''
            task build
        '''

        when:
        configurationCacheRun 'build'

        then:
        outputContains 'initscript1!'
        configurationCache.assertStateStored()

        when:
        gradleUserHomeDirFile('init.d/initscript2.gradle').tap {
            text = 'println("initscript2!")'
        }
        configurationCacheRun 'build'

        then:
        outputContains 'initscript1!'
        outputContains 'initscript2!'
        configurationCache.assertStateStored()
    }

    def "invalidates cache upon removing init script from Gradle home"() {

        requireOwnGradleUserHomeDir()

        given:
        def initScript1 = gradleUserHomeDirFile('init.d/initscript1.gradle.kts').tap {
            text = 'println("initscript1!")'
        }
        gradleUserHomeDirFile('init.d/initscript2.gradle').tap {
            text = 'println("initscript2!")'
        }
        buildFile << '''
            task build
        '''

        when:
        configurationCacheRun 'build'

        then:
        outputContains 'initscript1!'
        outputContains 'initscript1!'
        configurationCache.assertStateStored()

        when:
        initScript1.delete()
        configurationCacheRun 'build'

        then:
        outputDoesNotContain 'initscript1!'
        outputContains 'initscript2!'
        configurationCache.assertStateStored()
    }

    private File gradleUserHomeDirFile(String path) {
        executer.gradleUserHomeDir.file(path)
    }
}
