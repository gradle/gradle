/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles

class ScriptDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @LeaksFileHandles("Puts gradle user home in integration test dir")
    def "root component identifier has the correct type when resolving a script classpath"() {
        given:
        def module = mavenRepo().module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: 'classifier')
        module.publish()
        mavenRepo().module("org.gradle", "other", "preview-1").publish()

        and:
        settingsFile << """
rootProject.name = 'testproject'
"""

        buildFile << """
group = 'org.gradle'
version = '1.0'

buildscript {
    repositories { maven { url "${mavenRepo().uri}" } }
    dependencies {
        classpath "org.gradle:test:1.45"
    }
}

task check {
    doLast {
        assert buildscript.configurations.classpath.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
        def result = buildscript.configurations.classpath.incoming.resolutionResult

        // Check root component
        def rootId = result.root.id
        assert rootId instanceof ProjectComponentIdentifier
    }
}
"""

        expect:
        succeeds "check"
    }
}
