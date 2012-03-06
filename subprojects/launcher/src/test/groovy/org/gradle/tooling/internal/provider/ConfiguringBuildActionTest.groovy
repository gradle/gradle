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

import org.gradle.StartParameter
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 3/6/12
 */
class ConfiguringBuildActionTest extends Specification {

    def action = new ConfiguringBuildAction(null, null, null, null, [], null)
    def start = new StartParameter()

    def "allows configuring the start parameter with build arguments"() {
        given:
        start.projectProperties == [:]
        !start.dryRun

        when:
        action = new ConfiguringBuildAction(null, null, null, null, ['-PextraProperty=foo', '-m'], null)
        action.configureStartParameter(start)

        then:
        start.projectProperties['extraProperty'] == 'foo'
        start.dryRun
    }
}
