/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.rhino

import spock.lang.Specification
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.artifacts.Configuration

class RhinoPluginTest extends Specification {

    Project project = ProjectBuilder.builder().build()
    RhinoExtension extension

    def setup() {
        apply(plugin: RhinoPlugin)
        extension = javaScript.rhino
    }

    def methodMissing(String name, args) {
        project."$name"(*args)
    }

    def propertyMissing(String name) {
        project."$name"
    }

    def propertyMissing(String name, value) {
        project."$name" = value
    }

    def "extension is available"() {
        expect:
        extension != null
        extension.configuration instanceof Configuration
    }

    def "can register alternate dependency"() {
        when:
        extension.dependencies "junit:junit:4.10"

        then:
        extension.configuration.dependencies.size() == 1
    }

}
