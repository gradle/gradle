/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class ForcedModulesIntegrationTest extends AbstractIntegrationSpec {

    void "can force already resolved version of a module and avoid conflict"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url $mavenRepo }
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
}

allprojects {
    configurations.all {
	    resolutionStrategy {
	        force 'org:foo:1.3.3'
	        failOnVersionConflict()
	    }
	}
}

"""

        expect:
        run("api:dependencies", "tool:dependencies")
    }

    void "can force arbitrary version of a module and avoid conflict"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foobar", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()
        mavenRepo.module("org", "foo", '1.5.5').publish()

        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url $mavenRepo }
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
    task checkDeps(dependsOn: configurations.compile) << {
        assert configurations.compile*.name == ['api-1.0.jar', 'impl-1.0.jar', 'foo-1.5.5.jar']
        def metaData = configurations.compile.resolvedConfiguration
        def api = metaData.firstLevelModuleDependencies.find { it.moduleName == 'api' }
        assert api.children.size() == 1
        assert api.children.find { it.moduleName == 'foo' && it.moduleVersion == '1.5.5' }
        def impl = metaData.firstLevelModuleDependencies.find { it.moduleName == 'impl' }
        assert impl.children.size() == 1
        assert impl.children.find { it.moduleName == 'foo' && it.moduleVersion == '1.5.5' }
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            failOnVersionConflict()
            force 'org:foo:1.5.5'
        }
    }
}

"""

        expect:
        run(":tool:checkDeps")
    }

    void "latest strategy respects forced modules"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url $mavenRepo }
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
	configurations.all {
	    resolutionStrategy {
	        failOnVersionConflict()
	        force 'org:foo:1.3.3'
	    }
	}
    task checkDeps(dependsOn: configurations.compile) << {
        assert configurations.compile*.name == ['api.jar', 'impl.jar', 'foo-1.3.3.jar']
    }
}
"""

        expect:
        run("tool:checkDeps")
    }

    void "can force the version of a direct dependency"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        buildFile << """
apply plugin: 'java'
repositories {
    maven { url $mavenRepo }
}

dependencies {
    compile 'org:foo:1.4.4'
    compile ('org:foo:1.3.3') { force = true }
}

task checkDeps << {
    assert configurations.compile*.name == ['foo-1.3.3.jar']
}
"""

        expect:
        executer.withTasks("checkDeps").run()
    }

    void "forcing transitive dependency does not add extra dependency"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("hello", "world", '1.4.4').publish()

        buildFile << """
apply plugin: 'java'
repositories {
    maven { url $mavenRepo }
}

dependencies {
    compile 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'hello:world:1.4.4'
}

task checkDeps << {
    assert configurations.compile*.name == ['foo-1.3.3.jar']
}
"""

        expect:
        run("checkDeps")
    }

    //TODO SF add coverage with conflicting forced modules
}
