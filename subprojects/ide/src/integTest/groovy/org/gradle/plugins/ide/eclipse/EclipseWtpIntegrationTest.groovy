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
package org.gradle.plugins.ide.eclipse

import org.junit.Test
import spock.lang.Issue

class EclipseWtpIntegrationTest extends AbstractEclipseIntegrationTest {
    @Test
    @Issue("GRADLE-1415")
    void canUseSelfResolvingFiles() {
        def buildFile = """
apply plugin: "war"
apply plugin: "eclipse"

dependencies {
    compile fileTree(dir: "libs", includes: ["*.jar"])
}
        """

        def libsDir = file("libs")
        libsDir.mkdir()
        libsDir.createFile("foo.jar")

        // when
        runEclipseTask(buildFile)

        // then
        libEntriesInClasspathFileHaveFilenames("foo.jar")
    }

    @Test
    @Issue("GRADLE-2526")
    void overwritesDependentModules() {
        generateWebProjectWithWtpComponentDependency("1.0")
        def projectModules = parseComponentFile()
        assert getHandleFilenames(projectModules) == ["myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set

        generateWebProjectWithWtpComponentDependency("1.2.3")
        def projectModules2 = parseComponentFile()
        assert getHandleFilenames(projectModules2) == ["myartifact-1.2.3.jar", "myartifactdep-1.0.jar"] as Set
    }

    private generateWebProjectWithWtpComponentDependency(myArtifactVersion) {
        def repoDir = file("repo")
        maven(repoDir).module("mygroup", "myartifact", myArtifactVersion).dependsOnModules("myartifactdep").publish()
        maven(repoDir).module("mygroup", "myartifactdep").publish()

        file("build.gradle") << """\
            apply plugin: "eclipse-wtp"
            apply plugin: "war"

            configurations {
                wtpOnly
            }

            repositories {
                maven { url "${repoDir.toURI()}" }
            }

            dependencies {
                wtpOnly "mygroup:myartifact:$myArtifactVersion"
            }

            eclipse.wtp.component  {
                plusConfigurations += [ configurations.wtpOnly ]
            }
            """.stripIndent()

        executer.withTasks("eclipse").run()
    }

	private Set getHandleFilenames(projectModules) {
		projectModules."wb-module"."dependent-module".@handle*.text().collect { it.substring(it.lastIndexOf("/") + 1) } as Set
	}
}
