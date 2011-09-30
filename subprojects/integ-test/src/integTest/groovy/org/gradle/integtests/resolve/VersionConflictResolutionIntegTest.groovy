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
import org.junit.Rule
import org.junit.Test

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class VersionConflictResolutionIntegTest extends AbstractIntegrationTest {

    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void "strict conflict resolution should fail due to conflict"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", 1.33).publishArtifact()
        maven(repo).module("org", "foo", 1.44).publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.33')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.44')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
		versionConflictStrategy = VersionConflictStrategy.STRICT
	}
}
"""

        try {
            //when
            executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
                .withArguments("-s")
                .withTasks("tool:dependencies").run() //cannot runWithFailure until the error reporting is fixed

            //then
            assert false
        } catch(Exception e) {
            assert messages(e).contains('StrictConflictException')
        }
    }

    String messages(Exception e) {
        String out = e.toString()
        while(e.cause) {
            e = e.cause
            out += "\n$e"
        }
        out
    }

    @Test
    void "strict conflict resolution should pass when no conflicts"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", 1.33).publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.33')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.33')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
		versionConflictStrategy = VersionConflictStrategy.STRICT
	}
}
"""

            //when
            executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
                .withArguments("-s")
                .withTasks("tool:dependencies").run()

            //then no exceptions are thrown
    }

    @Test
    void "resolves to the latest version by default"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", 1.33).publishArtifact()
        maven(repo).module("org", "foo", 1.44).publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.33')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.44')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
}
"""

        //when
        def result = executer.usingBuildScript(buildFile)
                .usingSettingsFile(settingsFile).withArguments("-s")
                .withTasks("tool:dependencies").run()

        //then
        assert result.output.contains('1.44')
        assert !result.output.contains('1.33')
    }
}
