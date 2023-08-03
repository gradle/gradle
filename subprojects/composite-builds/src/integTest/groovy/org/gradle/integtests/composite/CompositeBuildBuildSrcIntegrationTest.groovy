/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildBuildSrcIntegrationTest extends AbstractIntegrationSpec {

    def "included and composing builds can contain buildSrc builds"() {
        def outerBuild = new BuildTestFile(testDirectory, "root")
        def childBuild = new BuildTestFile(testDirectory.file("child"), "child")

        outerBuild.settingsFile << """
            includeBuild 'child'
        """
        outerBuild.file('buildSrc/src/main/java/Thing.java') << """
            class Thing {
                Thing() { System.out.println("outer thing"); }
            }
        """
        outerBuild.buildFile << "new Thing()"

        childBuild.settingsFile << "rootProject.name = 'someBuild'"
        childBuild.file('buildSrc/src/main/java/Thing.java') << """
            class Thing {
                Thing() { System.out.println("child thing"); }
            }
        """
        childBuild.buildFile << "new Thing()"

        when:
        run("help")

        then:
        result.assertTaskExecuted(":buildSrc:jar")
        result.assertTaskExecuted(":child:buildSrc:jar")

        outputContains("outer thing")
        outputContains("child thing")
    }
}
