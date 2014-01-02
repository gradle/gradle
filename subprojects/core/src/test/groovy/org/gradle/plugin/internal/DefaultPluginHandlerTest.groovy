/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.internal

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.plugin.resolve.internal.DefaultPluginRequest
import org.gradle.plugin.resolve.internal.PluginResolution
import org.gradle.plugin.resolve.internal.PluginResolver
import spock.lang.Specification
import spock.lang.Unroll

class DefaultPluginHandlerTest extends Specification {

    def resolver = Mock(PluginResolver)
    def applicator = Mock(Action)

    def handler = new DefaultPluginHandler(resolver, applicator)

    @Unroll
    def "errors on invalid notation - #map"() {
        when:
        handler.apply(map)

        then:
        thrown InvalidUserDataException

        where:
        map << [
                [:],
                [foo: "bar"],
                [version: "1.0"],
                [version: 1],
                [plugin: 1],
                [plugin: 1, version: 1]
        ]
    }

    @Unroll
    def "accepts valid notation and applies when resolved - #map"() {
        given:
        def resolution = Mock(PluginResolution)

        when:
        handler.apply(map)

        then:
        1 * resolver.resolve(request) >> resolution
        1 * applicator.execute(resolution)

        where:
        map                             | request
        [plugin: "foo"]                 | new DefaultPluginRequest("foo")
        [plugin: "foo", version: "bar"] | new DefaultPluginRequest("foo", "bar")
    }

    @Unroll
    def "accepts valid notation and errors when unresolved - #map"() {
        when:
        handler.apply(map)

        then:
        1 * resolver.resolve(request) >> null

        and:
        thrown UnknownPluginException

        where:
        map                             | request
        [plugin: "foo"]                 | new DefaultPluginRequest("foo")
        [plugin: "foo", version: "bar"] | new DefaultPluginRequest("foo", "bar")
    }

}
