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

package org.gradle.api.internal.artifacts.dsl.dependencies


import org.gradle.api.artifacts.dsl.Dependencies
import spock.lang.Specification

class DependenciesExtensionModuleTest extends Specification {
    def "can call module with a #map"() {
        def dependencies = Mock(Dependencies)
        when:
        DependenciesExtensionModule.module(dependencies, map)
        then:
        1 * dependencies.module(group, name, version)
        0 * _._
        where:
        map                                                     | group         | name      | version
        [name: "example"]                                       | null          | "example" | null
        [group: "com.example", name: "example"]                 | "com.example" | "example" | null
        [name: "example", version: "1.0"]                       | null          | "example" | "1.0"
        [group: "com.example", name: "example", version: "1.0"] | "com.example" | "example" | "1.0"
    }

    def "does not allow extra keys in module(Map)"() {
        def map = [invalidKey: "value", group: "com.example", name: "example", version: "1.0"]
        def dependencies = Mock(Dependencies)
        when:
        DependenciesExtensionModule.module(dependencies, map)
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The map must not contain the following keys: [invalidKey]"
    }

    def "module(Map) must contain a name key"() {
        def map = [group: "com.example", version: "1.0"]
        def dependencies = Mock(Dependencies)
        when:
        DependenciesExtensionModule.module(dependencies, map)
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The map must contain a name key."
    }
}
