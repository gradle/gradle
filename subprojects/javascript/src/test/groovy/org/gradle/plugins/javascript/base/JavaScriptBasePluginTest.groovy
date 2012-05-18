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

package org.gradle.plugins.javascript.base

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

class JavaScriptBasePluginTest extends Specification {

    Project project = ProjectBuilder.builder().build()
    JavaScriptExtension extension

    def setup() {
        apply(plugin: JavaScriptBasePlugin)
        extension = javaScript
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
    }

    def "can get public repo"() {
        expect:
        extension.gradlePublicJavaScriptRepository instanceof MavenArtifactRepository
        MavenArtifactRepository repo = extension.gradlePublicJavaScriptRepository as MavenArtifactRepository
        repo.url.toString() == JavaScriptExtension.GRADLE_PUBLIC_JAVASCRIPT_REPO_URL
    }

}
