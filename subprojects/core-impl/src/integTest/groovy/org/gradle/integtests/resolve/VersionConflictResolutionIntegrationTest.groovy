/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.junit.Test
import spock.lang.Issue

import static org.hamcrest.Matchers.containsString

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class VersionConflictResolutionIntegrationTest extends AbstractIntegrationTest {

    @Test
    void "strict conflict resolution should fail due to conflict"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
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

	configurations.compile.resolutionStrategy.failOnVersionConflict()
}
"""

        //when
        def result = executer.withTasks("tool:dependencies").runWithFailure()

        //then
        result.assertThatCause(containsString('A conflict was found between the following modules:'))
    }

    @Test
    void "strict conflict resolution should pass when no conflicts"() {
        repo.module("org", "foo", '1.3.3').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
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

	configurations.all { resolutionStrategy.failOnVersionConflict() }
}
"""

        //when
        executer.withTasks("tool:dependencies").run()

        //then no exceptions are thrown
    }

    @Test
    void "strict conflict strategy can be used with forced modules"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()
        repo.module("org", "foo", '1.5.5').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
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
		compile('org:foo:1.5.5'){
		    force = true
		}
	}

	configurations.all { resolutionStrategy.failOnVersionConflict() }
}
"""

        //when
        executer.withTasks("tool:dependencies").run()

        //then no exceptions are thrown because we forced a certain version of conflicting dependency
    }

    @Test
    void "resolves to the latest version by default"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
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
    task checkDeps(dependsOn: configurations.compile) << {
        assert configurations.compile*.name == ['api.jar', 'impl.jar', 'foo-1.4.4.jar']
    }
}
"""

        //expect
        executer.withTasks("tool:checkDeps").run()
    }

    @Test
    void "can force the version of a particular module"() {
        repo.module("org", "foo", '1.3.3').publish()
        repo.module("org", "foo", '1.4.4').publish()

        def buildFile = file("build.gradle")
        buildFile << """
apply plugin: 'java'
repositories {
    maven { url "${repo.uri}" }
}

dependencies {
    compile 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'org:foo:1.4.4'
}

task checkDeps << {
    assert configurations.compile*.name == ['foo-1.4.4.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "does not attempt to resolve an evicted dependency"() {
        repo.module("org", "external", "1.2").publish()
        repo.module("org", "dep", "2.2").dependsOn("org", "external", "1.0").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url "${repo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps << {
    assert configurations.compile*.name == ['external-1.2.jar', 'dep-2.2.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "resolves dynamic dependency before resolving conflict"() {
        repo.module("org", "external", "1.2").publish()
        repo.module("org", "external", "1.4").publish()
        repo.module("org", "dep", "2.2").dependsOn("org", "external", "1.+").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url "${repo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps << {
    assert configurations.compile*.name == ['dep-2.2.jar', 'external-1.4.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "fails when version selected by conflict resolution does not exist"() {
        repo.module("org", "external", "1.2").publish()
        repo.module("org", "dep", "2.2").dependsOn("org", "external", "1.4").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url "${repo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps << {
    assert configurations.compile*.name == ['external-1.2.jar', 'dep-2.2.jar']
}
"""

        //expect
        def failure = executer.withTasks("checkDeps").runWithFailure()
        failure.assertHasCause("Could not find org:external:1.4.")
    }

    @Test
    void "does not fail when evicted version does not exist"() {
        repo.module("org", "external", "1.4").publish()
        repo.module("org", "dep", "2.2").dependsOn("org", "external", "1.4").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url "${repo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps << {
    assert configurations.compile*.name == ['dep-2.2.jar', 'external-1.4.jar']
}
"""

        //expect
        executer.withTasks("checkDeps").run()
    }

    @Test
    void "takes newest dynamic version when dynamic version forced"() {
        //given
        repo.module("org", "foo", '1.3.0').publish()

        repo.module("org", "foo", '1.4.1').publish()
        repo.module("org", "foo", '1.4.4').publish()
        repo.module("org", "foo", '1.4.9').publish()

        repo.module("org", "foo", '1.6.0').publish()

        def settingsFile = file("settings.gradle")
        settingsFile << "include 'api', 'impl', 'tool'"

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url "${repo.uri}" }
	}
}

project(':api') {
	dependencies {
		compile 'org:foo:1.4.4'
	}
}

project(':impl') {
	dependencies {
		compile 'org:foo:1.4.1'
	}
}

project(':tool') {

	dependencies {
		compile project(':api'), project(':impl'), 'org:foo:1.3.0'
	}

	configurations.all {
	    resolutionStrategy {
	        force 'org:foo:1.4+'
	        failOnVersionConflict()
	    }
	}

	task checkDeps << {
        assert configurations.compile*.name.contains('foo-1.4.9.jar')
    }
}

"""

        //expect
        executer.withTasks("tool:checkDeps").run()
    }

    @Test
    void "parent pom does not participate in forcing mechanism"() {
        //given
        repo.module("org", "foo", '1.3.0').publish()
        repo.module("org", "foo", '2.4.0').publish()

        def parent = repo.module("org", "someParent", "1.0")
        parent.type = 'pom'
        parent.dependsOn("org", "foo", "1.3.0")
        parent.publish()

        def otherParent = repo.module("org", "someParent", "2.0")
        otherParent.type = 'pom'
        otherParent.dependsOn("org", "foo", "2.4.0")
        otherParent.publish()

        def module = repo.module("org", "someArtifact", '1.0')
        module.parentPomSection = """
<parent>
  <groupId>org</groupId>
  <artifactId>someParent</artifactId>
  <version>1.0</version>
</parent>
"""
        module.publish()

        def buildFile = file("build.gradle")
        buildFile << """
apply plugin: 'java'
repositories {
    maven { url "${repo.uri}" }
}

dependencies {
    compile 'org:someArtifact:1.0'
}

configurations.all {
    resolutionStrategy {
        force 'org:someParent:2.0'
        failOnVersionConflict()
    }
}

task checkDeps << {
    def deps = configurations.compile*.name
    assert deps.contains('someArtifact-1.0.jar')
    assert deps.contains('foo-1.3.0.jar')
    assert deps.size() == 2
}
"""

        //expect
        executer.withTasks("checkDeps").withArguments('-s').run()
    }

    @Test
    void "previously evicted nodes should contain correct target version"() {
        /*
        a1->b1
        a2->b2->a1

        resolution process:

        1. stop resolution, resolve conflict a1 vs a2
        2. select a2, restart resolution
        3. stop, resolve b1 vs b2
        4. select b2, restart
        5. resolve b2 dependencies, a1 has been evicted previously but it should show correctly on the report
           ('dependencies' report pre 1.2 would not show the a1 dependency leaf for this scenario)
        */

        ivyRepo.module("org", "b", '1.0').publish()
        ivyRepo.module("org", "a", '1.0').dependsOn("org", "b", '1.0').publish()
        ivyRepo.module("org", "b", '2.0').dependsOn("org", "a", "1.0").publish()
        ivyRepo.module("org", "a", '2.0').dependsOn("org", "b", '2.0').publish()

        file("build.gradle") << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:a:1.0', 'org:a:2.0'
            }
            task checkDeps << {
                assert configurations.conf*.name == ['a-2.0.jar', 'b-2.0.jar']
                def result = configurations.conf.incoming.resolutionResult
                assert result.allModuleVersions.size() == 3
                def root = result.root
                assert root.dependencies*.toString() == ['org:a:1.0 -> org:a:2.0', 'org:a:2.0']
                def a = result.allModuleVersions.find { it.id.name == 'a' }
                assert a.dependencies*.toString() == ['org:b:2.0']
                def b = result.allModuleVersions.find { it.id.name == 'b' }
                assert b.dependencies*.toString() == ['org:a:1.0 -> org:a:2.0']
            }
        """

        executer.withTasks("checkDeps").run()
    }

    @Test
    @Issue("GRADLE-2555")
    void "can deal with transitive with parent in conflict"() {
        /*
            Graph looks like…

            \--- org:a:1.0
                 \--- org:in-conflict:1.0 -> 2.0
                      \--- org:target:1.0
                           \--- org:target-child:1.0
            \--- org:b:1.0
                 \--- org:b-child:1.0
                      \--- org:in-conflict:2.0 (*)

            This is the simplest structure I could boil it down to that produces the error.
            - target *must* have a child
            - Having "b" depend directly on "in-conflict" does not produce the error, needs to go through "b-child"
         */

        mavenRepo.module("org", "target-child", "1.0").
                publish()

        mavenRepo.module("org", "target", "1.0").
                dependsOn("org", "target-child", "1.0").
                publish()

        mavenRepo.module("org", "in-conflict", "1.0").
                dependsOn("org", "target", "1.0").
                publish()

        mavenRepo.module("org", "in-conflict", "2.0").
                dependsOn("org", "target", "1.0").
                publish()

        mavenRepo.module("org", "a", '1.0').
                dependsOn("org", "in-conflict", "1.0").
                publish()

        mavenRepo.module("org", "b-child", '1.0').
                dependsOn("org", "in-conflict", "2.0").
                publish()

        mavenRepo.module("org", "b", '1.0').
                dependsOn("org", "b-child", "1.0").
                publish()

        when:
        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations { conf }

            dependencies {
                conf "org:a:1.0", "org:b:1.0"
            }

        task checkDeps << {
            assert configurations.conf*.name == ['a-1.0.jar', 'b-1.0.jar', 'target-child-1.0.jar', 'target-1.0.jar', 'in-conflict-2.0.jar', 'b-child-1.0.jar']
            def result = configurations.conf.incoming.resolutionResult
            assert result.allModuleVersions.size() == 7
            def a = result.allModuleVersions.find { it.id.name == 'a' }
            assert a.dependencies*.toString() == ['org:in-conflict:1.0 -> org:in-conflict:2.0']
            def bChild = result.allModuleVersions.find { it.id.name == 'b-child' }
            assert bChild.dependencies*.toString() == ['org:in-conflict:2.0']
            def target = result.allModuleVersions.find { it.id.name == 'target' }
            assert target.dependents*.from*.toString() == ['org:in-conflict:2.0']
        }
        """

        executer.withTasks("checkDeps").run()
    }

    //TODO SF add coverage with conflicting forced modules

    def getRepo() {
        return maven(file("repo"))
    }
}
