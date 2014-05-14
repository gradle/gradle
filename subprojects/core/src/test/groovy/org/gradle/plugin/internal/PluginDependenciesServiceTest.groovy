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

import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.plugin.use.internal.DefaultPluginRequest
import org.gradle.plugin.use.internal.PluginDependenciesService
import org.gradle.plugin.use.internal.PluginRequest
import spock.lang.Specification

class PluginDependenciesServiceTest extends Specification {

    final scriptSource = new StringScriptSource("d", "c")

    List<PluginRequest> plugins(Closure<?> closure) {
        new PluginDependenciesService(scriptSource).with {
            createSpec(10).with(closure)
            getRequests()
        }
    }

    List<PluginRequest> requests(Map<String, String> requests) {
        requests.collect { new DefaultPluginRequest(it.key, it.value, 10, scriptSource) }
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

    def "does not prevent duplicate ids"() {
        expect:
        (requests(foo: "1.0") * 2) == plugins {
            id "foo" version "1.0"
            id "foo" version "1.0"
        }
    }

}