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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.util.TestFile
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * @author Szczepan Faber, @date 03.03.11
 */
@Ignore
class VersionConflictResolutionIntegTest extends AbstractIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void shouldFailAndReportVersionConflict() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", 1.3).publishArtifact()
        maven(repo).module("org", "foo", 1.4).publishArtifact()

        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.toURI()}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
	//configurations.compile.versionConflictStrategy = VersionConflictStrategy.STRICT
}
"""

        try {
            //when
            executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
                .withArguments("-s")
                .withTasks("tool:dependencies").run() //for some reason I cannot use runWithFailure

            //then
            //assert false
        } catch(Exception e) {
            //assert e.cause.cause.cause.message.contains('StrictConflictException') //nice, huh? :D
        }
    }

    @Test
    void shouldBeHappyBecauseVersionsMatch() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", 1.3).publishArtifact()
        maven(repo).module("org", "foo", 1.4).publishArtifact()

        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.toURI()}" }
	}
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
	//configurations.compile.versionConflictStrategy = VersionConflictStrategy.STRICT
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
            .withArguments("-s")
            .withTasks("tool:dependencies").run()

        //then no errors
    }
}
