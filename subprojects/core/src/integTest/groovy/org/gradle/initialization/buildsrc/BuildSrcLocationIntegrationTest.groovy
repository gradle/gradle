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

class BuildSrcLocationIntegrationTest extends AbstractIntegrationSpec {

    def "buildSrc directory is always relative to settings dir"() {
        when:
        file("buildSrc/src/main/groovy/Thing.groovy") << "class Thing {}"
        settingsFile << """
            rootProject.projectDir = new File(rootDir, 'root')
        """

        def movedBuildScript = file("root/build.gradle") << ""

        executer.expectDocumentedDeprecationWarning("Specifying custom settings file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        executer.expectDocumentedDeprecationWarning("Specifying custom build file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        args('help', "-b", movedBuildScript.absolutePath, "-c", settingsFile.absolutePath)

        succeeds("help")

        then:
        executed(":buildSrc:compileGroovy")
    }

    def "buildSrc without settings file can execute standalone"() {
        given:
        settingsFile << "throw new GradleException('this should not be evaluated')"
        def buildSrc = file("buildSrc")
        buildSrc.file("build.gradle") << ''

        when:
        executer.usingProjectDirectory(buildSrc)

        then:
        succeeds("help")
    }

    def "empty buildSrc directory is ignored"() {
        file("buildSrc").createDir()

        when:
        succeeds("help")

        then:
        notExecuted(":buildSrc:compileGroovy", ":buildSrc:jar", ":buildSrc:build")
    }

    def "buildSrc directory with only buildSrc jar file is ignored"() {
        file("buildSrc/src/main/java/org/acme/build/SomeBuildSrcClass.java") << """
package org.acme.build;

public class SomeBuildSrcClass {}
"""
        when: // Create the initial buildSrc jar
        succeeds("help")
        executed(":buildSrc:jar")
        def originalBuildSrcJar = file("buildSrc/build/libs/buildSrc.jar").assertIsFile()
        def originalBuildSrcJarState = originalBuildSrcJar.snapshot()

        and: // Remove the src directory, leaving only the generated jar
        file("buildSrc/src").deleteDir()

        and: // Run another build, checking that `buildSrc.jar` is not in the class path
        buildFile << """
            try {
                getClass().classLoader.loadClass("org.acme.build.SomeBuildSrcClass")
                throw new IllegalStateException("Class should not be visible")
            } catch (ClassNotFoundException e) {
                // Expected
            }
"""
        succeeds("help")

        then:
        // The original buildSrc jar remains intact, but is not added to the build class path
        originalBuildSrcJar.assertHasNotChangedSince(originalBuildSrcJarState)
        notExecuted(":buildSrc:jar")
    }

}
