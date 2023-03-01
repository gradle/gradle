/*
 * Copyright 2022 the original author or authors.
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

import spock.lang.Issue

class ConfigurationCacheProcessResourcesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue('https://github.com/gradle/gradle/issues/16423')
    def 'java source files are excluded by default'() {
        given:
        buildFile '''
            plugins {
                id 'java'
            }
            sourceSets {
                main.resources.srcDir 'src/main/java'
                main.resources.exclude '**/*.kt' // Forces an intersection pattern set to be created behind the scenes
            }
        '''
        createDir('src/main') {
            dir('java') {
                file('Test.java') << 'class Test {}'
            }
            dir('resources') {
                file('data.txt') << '42'
            }
        }
        def configurationCache = newConfigurationCacheFixture()

        when: ':processResources is executed twice'
        configurationCacheRun 'processResources'
        configurationCacheRun 'processResources'

        then: 'the 2nd time it is loaded from the cache'
        configurationCache.assertStateLoaded()

        and: 'the task is up-to-date'
        result.assertTaskSkipped ':processResources'
    }
}
