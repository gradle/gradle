/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects

class BuildSrcVisibilityIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForIsolatedProjects(because = "Investigate")
    def "buildSrc classes are not visible in settings"() {
        file('buildSrc/build.gradle') << """
            apply plugin: 'groovy'

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-math3:3.6.1'
            }
        """

        file('buildSrc/src/main/java/org/acme/build/SomeBuildSrcClass.java') << """
        package org.acme.build;

        public class SomeBuildSrcClass {
            public static void foo(String foo){
                System.out.println(foo);
            }
        }
        """
        file('buildSrc/src/main/java/org/acme/build/SomeOtherBuildSrcClass.java') << """
        package org.acme.build;

        public class SomeOtherBuildSrcClass {
            public static void foo(String foo){
                System.out.println(foo);
            }
        }
        """

        def localClassName = "org.acme.build.SomeBuildSrcClass"
        def dependencyClassName = "org.apache.commons.math3.util.FastMath"

        settingsFile << """

            gradle.ext.tryClass = { String from, String name, ClassLoader classLoader ->
                try {
                    classLoader.loadClass(name)
                    println "FOUND [\$from] \$name"
                } catch (ClassNotFoundException e) {
                    println "NOT FOUND [\$from] \$name"
                }
            }

            gradle.tryClass("settings", "$localClassName", getClass().classLoader)
            gradle.tryClass("settings", "$dependencyClassName", getClass().classLoader)
        """

        buildFile << """
            gradle.tryClass("project", "$localClassName", project.buildscript.classLoader)
            gradle.tryClass("project", "$dependencyClassName", project.buildscript.classLoader)
        """

        when:
        succeeds("help")

        then:
        outputContains("NOT FOUND [settings] $localClassName")
        outputContains("NOT FOUND [settings] $dependencyClassName")
        outputContains("FOUND [project] $localClassName")
        outputContains("FOUND [project] $dependencyClassName")
    }

}
