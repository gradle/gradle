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

package org.gradle.buildinit.plugins.internal

import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class WrapperPluginAutoApplyActionIntegTest extends AbstractIntegrationSpec {
    final wrapper = new WrapperTestFixture(testDirectory)

    def "can apply wrapper plugin dynamically"() {
        when:
        run 'tasks'
        then:
        output.contains("wrapper - Generates Gradle wrapper files.")

        when:
        run 'wrapper'
        then:
        wrapper.generated()
    }

    def "can use camel-case for dynamically applied wrapper plugin "() {
        when:
        run taskName
        then:
        wrapper.generated()
        where:
        taskName << ["wrapp"]//, "wrap", "w"]
    }

    def "wrapper plugin not applied on subprojects"() {
        setup:
        settingsFile << "include 'moduleA'"
        TestFile subprojectsDir = file("moduleA").createDir()

        when:
        executer.inDirectory(subprojectsDir)
        run 'tasks'
        then:
        !output.contains("wrapper - Generates Gradle wrapper files.")

        when:
        executer.inDirectory(subprojectsDir)
        then:
        fails("wrapper")
    }

    def "can reference dynamic applied wrapper plugin"() {
        when:
        buildFile << """
            wrapper{
                gradleVersion = '12.34'
            }
    """
        run 'wrapper', '--no-validate-url'
        then:
        wrapper.generated("12.34")
    }

    def "can depend on dynamic applied wrapper task"() {
        when:
        buildFile << """
            task myTask(dependsOn:wrapper)
        """
        run 'myTask'
        then:
        wrapper.generated()
    }
}
