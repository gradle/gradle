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

package org.gradle.api.internal.notations


import org.gradle.internal.typeconversion.NotationParser
import org.gradle.plugin.use.PluginDependency
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PluginDependencyNotationParserTest extends Specification {

    @Subject
    NotationParser<Object, PluginDependency> parser = PluginDependencyNotationParser.parser({ it })

    @Unroll("Parses #notation")
    def "can parse a plugin dependency notation"() {
        when:
        def dependency = parser.parseNotation(notation)

        then:
        dependency instanceof PluginDependency
        dependency.pluginId == expectedPluginId
        dependency.version.getRequiredVersion() == expectedVersion

        where:
        notation                                    | expectedPluginId | expectedVersion
        'i:v'                                       | 'i'              | 'v'
        'i'                                         | 'i'              | ''
        'id:1.0'                                    | 'id'             | '1.0'
        'id'                                        | 'id'             | ''
        'id  :1.0  '                                | 'id'             | '1.0'
        "${-> 'id'}:1.1"                            | 'id'             | '1.1'
        [id: 'pluginId']                            | 'pluginId'       | ''
        [id: 'pluginId', version: '1.4-beta-1']     | 'pluginId'       | '1.4-beta-1'
        [id: ' pluginId ', version: '  1.4-beta-1'] | 'pluginId'       | '1.4-beta-1'
    }

    @Unroll("Fails to parse #notation")
    def "fails to parse map notation which doesn't pass validation"() {
        when:
        parser.parseNotation(notation)

        then:
        Exception ex = thrown()
        ex.message.startsWith(error)

        where:
        notation               | error
        'id:1.0:somethingElse' | "Supplied String plugin notation 'id:1.0:somethingElse' is invalid"
        [:]                    | "Required keys [id] are missing from map {}."
        [version: 'foo']       | "Required keys [id] are missing from map {version=foo}."
    }

}
