/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec

class JavaProjectCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    def "can upgrade and downgrade Gradle version used to build Java project"() {
        given:
        buildFile << """
apply plugin: 'java'

task custom(type: org.gradle.CustomTask)
        """

        and:
        file('src/main/java/org/gradle/Person.java') << """
package org.gradle;
class Person { }
"""

        and:
        file('buildSrc/src/main/groovy/org/gradle/CustomTask.groovy') << """
package org.gradle
class CustomTask extends org.gradle.api.DefaultTask { }
"""

        expect:
        version previous withTasks 'build' run()
        version current withTasks 'build' run()
        version previous withTasks 'build' run()
    }
}
