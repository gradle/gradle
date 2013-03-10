/*
 * Copyright 2009 the original author or authors.
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



package org.gradle.tooling.internal.provider

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 3/6/12
 */
class ConfiguringBuildActionTest extends Specification {
    @Rule TestNameTestDirectoryProvider temp

    def "allows configuring the start parameter with build arguments"() {
        when:
        def action = new ConfiguringBuildAction(arguments: ['-PextraProperty=foo', '-m'])
        def start = action.configureStartParameter()

        then:
        start.projectProperties['extraProperty'] == 'foo'
        start.dryRun
    }

    def "can overwrite project dir via build arguments"() {
        given:
        def projectDir = temp.createDir('projectDir')

        when:
        def action = new ConfiguringBuildAction(projectDirectory: projectDir, arguments: ['-p', 'otherDir'])
        def start = action.configureStartParameter()

        then:
        start.projectDir == new File(projectDir, "otherDir")
    }

    def "can overwrite gradle user home via build arguments"() {
        given:
        def dotGradle = temp.createDir('.gradle')
        def projectDir = temp.createDir('projectDir')

        when:
        def action = new ConfiguringBuildAction(gradleUserHomeDir: dotGradle, projectDirectory: projectDir, 
                arguments: ['-g', 'otherDir'])
        def start = action.configureStartParameter()

        then:
        start.gradleUserHomeDir == new File(projectDir, "otherDir")
    }

    def "can overwrite searchUpwards via build arguments"() {
        when:
        def action = new ConfiguringBuildAction(arguments: ['-u'])
        def start = action.configureStartParameter()

        then:
        !start.searchUpwards
    }

    def "searchUpwards configured directly on the action wins over the command line setting"() {
        when:
        def action = new ConfiguringBuildAction(arguments: ['-u'], searchUpwards: true)
        def start = action.configureStartParameter()

        then:
        start.searchUpwards
    }

    def "can overwrite configure on demand via build arguments"() {
        expect:
        !new ConfiguringBuildAction().configureStartParameter().configureOnDemand

        when:
        def action = new ConfiguringBuildAction(arguments: ['--configure-on-demand'])
        def start = action.configureStartParameter()

        then:
        start.configureOnDemand
    }
}
