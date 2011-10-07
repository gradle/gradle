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
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()
        maven(repo).module("org", "foo", '1.4.4').publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}

	configurations.compile.versionConflictStrategy.type = configurations.compile.versionConflictStrategy.strict()
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
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}

	configurations.all { versionConflictStrategy.type = versionConflictStrategy.strict() }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
            .withArguments("-s")
            .withTasks("tool:dependencies").run()

        //then no exceptions are thrown
    }

    @Test
    void "strict conflict strategy can be used with forced versions"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()
        maven(repo).module("org", "foo", '1.4.4').publishArtifact()
        maven(repo).module("org", "foo", '1.5.5').publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
		compile (group: 'org', name: 'foo', version:'1.5.5') {
		    force = true
		}
	}

	configurations.all { versionConflictStrategy.type = versionConflictStrategy.strict() }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
            .withArguments("-s")
            .withTasks("tool:dependencies").run()

        //then no exceptions are thrown because we forced a certain version of conflicting dependency
    }

    @Test
    void "strict conflict strategy with forced transitive dependency that is already resolved"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()
        maven(repo).module("org", "foo", '1.4.4').publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}

	configurations.all {
	    versionConflictStrategy.type = versionConflictStrategy.strict {
	        force = ['org:foo:1.3.3']
	    }
	}
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile)
            .withArguments("-s")
            .withTasks("tool:dependencies").run()

        //then no exceptions are thrown because we forced a certain version of conflicting dependency
        //TODO SF add coverage that checks if dependencies are not pushed to 1st level (write a functional test for it)
    }

    @Test
    void "can force the version of the dependency"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()
        maven(repo).module("org", "foo", '1.4.4').publishArtifact()

        def buildFile = file("master/build.gradle")
        buildFile << """
apply plugin: 'java'
repositories {
    maven { url "${repo.toURI()}" }
}

configurations {
    forcedVersions
}

dependencies {
    compile 'org:foo:1.3.3'
    forcedVersions 'org:foo:1.4.4'
}

configurations.all {
    resolution.forcedVersions = configurations.forcedVersions.incoming.dependencies
}
"""

        //when
        def result = executer.usingBuildScript(buildFile).withArguments("-s").withTasks("dependencies").run()

        //then
        assert result.output.contains("1.4.4")
        assert !result.output.contains("1.3.3")
    }

    @Test
    void "can force the version of transitive dependency and avoid conflict"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()
        maven(repo).module("org", "foobar", '1.3.3').publishArtifact()
        maven(repo).module("org", "foo", '1.4.4').publishArtifact()
        maven(repo).module("org", "foo", '1.5.5').publishArtifact()

        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	apply plugin: 'maven'
	repositories {
		maven { url "${repo.toURI()}" }
	}
	group = 'org.foo.unittests'
	version = '1.0'
}

project(':api') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		compile project(':api')
		compile project(':impl')
	}
}

allprojects {
    configurations {
	    forcedVersions
	}

	dependencies {
	    forcedVersions 'org:foo:1.5.5'
	}

    configurations.all {
        versionConflictStrategy.type = versionConflictStrategy.strict() {
            force = ['org:foo:1.5.5']
        }
        resolution.forcedVersions = configurations.forcedVersions.incoming.dependencies
    }

    task genIvy(type: Upload) {
        uploadDescriptor = true
        configuration = configurations.archives
        descriptorDestination = file('ivy.xml')
    }
}
"""

        //when
        def result = executer
                .usingBuildScript(buildFile).usingSettingsFile(settingsFile)
                .withArguments("-s").withTasks(":tool:dependencies", "install", "genIvy").run()

        //then
        assert result.output.contains("1.5.5")
        assert !result.output.contains("1.4.4")
        assert !result.output.contains("1.3.3")
    }

    @Test
    void "resolves to the latest version by default"() {
        TestFile repo = file("repo")
        maven(repo).module("org", "foo", '1.3.3').publishArtifact()
        maven(repo).module("org", "foo", '1.4.4').publishArtifact()

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
		compile (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		compile (group: 'org', name: 'foo', version:'1.4.4')
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
        assert result.output.contains('1.4.4')
        assert !result.output.contains('1.3.3')
    }
}
