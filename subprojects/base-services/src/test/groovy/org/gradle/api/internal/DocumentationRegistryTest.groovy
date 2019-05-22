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

package org.gradle.api.internal

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class DocumentationRegistryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    final GradleVersion gradleVersion = GradleVersion.current()
    final DocumentationRegistry registry = new DocumentationRegistry()

    def "points users at the gradle docs web site"() {
        expect:
        registry.getDocumentationFor('gradle_daemon') == "https://docs.gradle.org/${gradleVersion.version}/userguide/gradle_daemon.html"
    }

    def "points users at the gradle docs web site with section"() {
        expect:
        registry.getDocumentationFor('gradle_daemon', 'reusing_daemons') == "https://docs.gradle.org/${gradleVersion.version}/userguide/gradle_daemon.html#reusing_daemons"
    }

    def "points users at the gradle dsl web site"() {
        expect:
        registry.getDslRefForProperty(org.gradle.api.Action.class, 'execute') == "https://docs.gradle.org/${gradleVersion.version}/dsl/org.gradle.api.Action.html#org.gradle.api.Action:execute"
    }

    def "points users at the gradle guides"() {
        expect:
        registry.getGuideFor("creating-new-gradle-builds") == "https://guides.gradle.org/creating-new-gradle-builds"
    }

    def "points users at the gradle guides for topic"() {
        expect:
        registry.getTopicGuidesFor("Native") == "https://guides.gradle.org?q=Native"
    }
}
