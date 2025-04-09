/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppLib
import spock.lang.Issue

class CppGeneratedSourceIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle/issues/29767")
    def "can generate application's conventional sources"() {
        def app = new CppApp()

        given:
        app.sources.writeToSourceDir(file('staging-sources'))
        app.headers.writeToSourceDir(file('src/main/headers'))

        and:
        buildFile << '''
            apply plugin: 'cpp-application'

            def generatorTask = tasks.register('generateSources', Sync) {
                from(rootProject.file('staging-sources'))
                into('src/main/cpp')
            }

            application.source.builtBy(generatorTask)
        '''

        when:
        succeeds ":compileDebugCpp"
        then:
        result.assertTasksExecuted(":generateSources", ":compileDebugCpp")
    }

    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle/issues/29767")
    def "can generate library's conventional sources"() {
        def lib = new CppLib()

        given:
        lib.sources.writeToSourceDir(file('staging-sources'))
        lib.publicHeaders.writeToSourceDir(file('src/main/public'))
        lib.privateHeaders.writeToSourceDir(file('src/main/headers'))

        and:
        buildFile << '''
            apply plugin: 'cpp-library'

            def generatorTask = tasks.register('generateSources', Sync) {
                from(rootProject.file('staging-sources'))
                into('src/main/cpp')
            }

            library.source.builtBy(generatorTask)
        '''

        when:
        succeeds ":compileDebugCpp"
        then:
        result.assertTasksExecuted(":generateSources", ":compileDebugCpp")
    }
}
