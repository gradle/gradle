/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r34

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

class BuildActionCrossVersionSpec extends ToolingApiSpecification {

    @Issue("https://github.com/gradle/gradle/issues/1180")
    def "can load custom action from url containing whitespaces"() {
        setup:
        def jar = getActionJarWithSpacesInPath()

        when:
        def classloader = new URLClassLoader([jar.toURL()] as URL[], getClass().classLoader)
        def action = classloader.loadClass("ActionImpl").getConstructor().newInstance()
        withConnection { ProjectConnection connection ->
            connection.action(action).run()
        }

        then:
        notThrown Exception

        cleanup:
        classloader?.close()
    }

    private File getActionJarWithSpacesInPath() {
        file("work folder/other/settings.gradle") << """
            rootProject.name = 'other'
        """
        file("work folder/other/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(gradleApi())
            }
        """
        file('work folder/other/src/main/java/ActionImpl.java') << """
            public class ActionImpl implements ${BuildAction.name}<Void> {
                public Void execute(${BuildController.name} controller) {
                    return null;
                }
            }
        """

        connector(file("work folder/other"))
            .connect()
            .newBuild()
            .forTasks("jar")
            .run()

        return file("work folder/other/build/libs/other.jar")
    }
}
