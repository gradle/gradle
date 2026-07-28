/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry

class TaskConfigurationAvoidanceFuncTest extends AbstractGeneralPluginFuncTest {

    def "test tasks are not created from use of plugin (gradle version #gradleVersion)"() {
        when:
        buildFile.text = baseBuildScriptWithoutPlugin() + listenerAndTaskRegistration()
        def result = gradleRunner(gradleVersion)
            .withArguments("help")
            .build()

        then:
        !result.output.contains("CREATED TASK ")

        when:
        buildFile.text = baseBuildScript() + listenerAndTaskRegistration()
        result = gradleRunner(gradleVersion)
            .withArguments("help")
            .build()

        then:
        !result.output.contains("CREATED TASK ")

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    static String listenerAndTaskRegistration() {
        """
            tasks.withType(Test).configureEach { println 'CREATED TASK ' + it.name }
            tasks.register("test1", Test)
        """
    }
}
