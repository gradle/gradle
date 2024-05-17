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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class ForcedModulesIntegrationTest extends AbstractIntegrationSpec {
    private ResolveTestFixture resolve = new ResolveTestFixture(buildFile)

    def setup() {
        resolve.addDefaultVariantDerivationStrategy()
    }

    void "can force the version of a particular module"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        buildFile << """
apply plugin: 'java'
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    implementation 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'org:foo:1.4.4'
}

task checkDeps {
    def compileClasspath = configurations.compileClasspath
    doLast {
        assert compileClasspath*.name == ['foo-1.4.4.jar']
    }
}
"""

        expect:
        run("checkDeps")
    }

    void "can force the version of a transitive dependency module"() {
        mavenRepo.module("org", "foo", '1.3.3')
            .dependsOn("org", "bar", '1.1')
            .publish()
        mavenRepo.module("org", "bar", '1.0').publish()

        buildFile << """
apply plugin: 'java'
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    implementation 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'org:bar:1.0'
}

task checkDeps {
    def compileClasspath = configurations.compileClasspath
    doLast {
        assert compileClasspath*.name == ['foo-1.3.3.jar', 'bar-1.0.jar']
    }
}
"""

        expect:
        run("checkDeps")
    }

    void "can force already resolved version of a module and avoid conflict"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        createDirs("api", "impl", "tool")
        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories { maven { url "${mavenRepo.uri}" } }
}

project(':api') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {

	dependencies {
		implementation project(':api')
		implementation project(':impl')
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

        createDirs("api", "impl", "tool")
        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories { maven { url "${mavenRepo.uri}" } }
	group = 'org.foo.unittests'
	version = '1.0'
}

project(':api') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':impl') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		implementation project(':api')
		implementation project(':impl')
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
        resolve.expectDefaultConfiguration("runtimeElements")
        resolve.prepare("runtimeClasspath")

        expect:
        run(":tool:checkDeps")
        resolve.expectGraph {
            root(":tool", "org.foo.unittests:tool:1.0") {
                project(":api", "org.foo.unittests:api:1.0") {
                    edge("org:foo:1.4.4", "org:foo:1.5.5") {
                        forced()
                    }
                }
                project(":impl", "org.foo.unittests:impl:1.0") {
                    edge("org:foo:1.3.3", "org:foo:1.5.5")
                }
            }
        }
    }

    void "latest strategy respects forced modules"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        createDirs("api", "impl", "tool")
        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories { maven { url "${mavenRepo.uri}" } }
}

project(':api') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		implementation project(':api')
		implementation project(':impl')
	}
	configurations.all {
	    resolutionStrategy {
	        failOnVersionConflict()
	        force 'org:foo:1.3.3'
	    }
	}
    task checkDeps {
        def runtimeClasspath = configurations.runtimeClasspath
        doLast {
            assert runtimeClasspath*.name == ['api.jar', 'impl.jar', 'foo-1.3.3.jar']
        }
    }
}
"""

        expect:
        run("tool:checkDeps")
    }

    void "forcing transitive dependency does not add extra dependency"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("hello", "world", '1.4.4').publish()

        buildFile << """
apply plugin: 'java'
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    implementation 'org:foo:1.3.3'
}

configurations.all {
    resolutionStrategy.force 'hello:world:1.4.4'
}

task checkDeps {
    def compileClasspath = configurations.compileClasspath
    doLast {
        assert compileClasspath*.name == ['foo-1.3.3.jar']
    }
}
"""

        expect:
        run("checkDeps")
    }

    void "when forcing the same module last declaration wins"() {
        mavenRepo.module("org", "foo", '1.9').publish()

        buildFile << """
apply plugin: 'java'
repositories { maven { url "${mavenRepo.uri}" } }

dependencies {
    implementation 'org:foo:1.0'
}

configurations.all {
    resolutionStrategy {
        force 'org:foo:1.5'
        force 'org:foo:2.0'
        force 'org:foo:1.9'
    }
}

task checkDeps {
    def compileClasspath = configurations.compileClasspath
    doLast {
        assert compileClasspath*.name == ['foo-1.9.jar']
    }
}
"""

        expect:
        run("checkDeps")
    }
}
