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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenPublishRepoDslIntegrationTest extends AbstractIntegrationSpec {
    def "repository DSL closure uses owner first resolution rather than the usual delegate first"() {
        executer.expectDeprecationWarning()

        buildFile << """
apply plugin: 'maven'

ext.value = 42

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "$mavenRepo.uri") {
                assert delegate.project == null // the repository's `project` property, should be null
                assert project != null // owner's `project` property
                println "value: " + project.value
                authentication(userName: 'user') // delegate's method
            }
        }
    }
}
"""

        when:
        run()

        then:
        output.contains("value: 42")
    }
}
