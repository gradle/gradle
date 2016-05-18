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

package org.gradle.plugin.use.internal

import org.gradle.api.Project
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugin.internal.PluginId
import org.gradle.plugin.use.PluginDependenciesSpec
import spock.lang.Specification

class ProjectPluginRequestCollectorTest extends Specification {

    final scriptSource = new StringScriptSource("d", "c")

    List<PluginRequest> plugins(@DelegatesTo(PluginDependenciesSpec) Closure<?> closure) {
        new ProjectPluginRequestCollector(scriptSource, Stub(Project), Stub(Instantiator)).with {
            with(closure)
            getRequests().collect { it.request }
        }
    }

    List<PluginRequest> requests(Map<String, String> requests) {
        requests.collect { new DefaultPluginRequest(PluginId.of(it.key), it.value, scriptSource.getDisplayName()) }
    }

    def "can use spec dsl to build one request"() {
        expect:
        requests(foo: "bar") == plugins {
            id "foo" version "bar"
        }
    }

    def "version is optional"() {
        expect:
        requests(foo: null) == plugins {
            id "foo"
        }
    }

    def "returns empty list if none specified"() {
        expect:
        plugins {}.isEmpty()
    }

    def "can specify multiple"() {
        expect:
        requests(foo: "1.0", "bar": "2.0") == plugins {
            id "foo" version "1.0"
            id "bar" version "2.0"
        }
    }

}
