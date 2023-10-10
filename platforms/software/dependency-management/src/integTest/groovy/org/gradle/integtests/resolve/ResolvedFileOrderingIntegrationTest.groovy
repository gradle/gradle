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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class ResolvedFileOrderingIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        settingsFile << """
rootProject.name = 'test'
"""
        buildFile << """
allprojects {
    dependencies {
        attributesSchema {
           attribute(Attribute.of('usage', String))
        }
    }
    configurations {
        compile
        "default" {
            extendsFrom compile
        }
    }
}
"""
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "result includes files from local and external components and file dependencies in a fixed order and with duplicates removed"() {
        mavenRepo.module("org", "test", "1.0")
            .artifact(classifier: 'from-main')
            .artifact(classifier: 'from-a')
            .artifact(classifier: 'from-c')
            .publish()
        mavenRepo.module("org", "test2", "1.0").publish()
        mavenRepo.module("org", "test3", "1.0").publish()

        settingsFile << """
include 'a', 'b', 'c'
"""
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
}
dependencies {
    compile files('test-lib.jar')
    compile project(':a')
    compile 'org:test:1.0'
    compile('org:test:1.0') {
        artifact {
            name = 'test'
            classifier = 'from-main'
            type = 'jar'
        }
    }
    artifacts {
        compile file('test.jar')
    }
}
project(':a') {
    dependencies {
        compile files('a-lib.jar')
        compile project(':b')
        compile project(':c')
        compile 'org:test:1.0'
        compile('org:test:1.0') {
            artifact {
                name = 'test'
                classifier = 'from-a'
                type = 'jar'
            }
        }
        compile('org:test:1.0') {
            artifact {
                name = 'test'
                classifier = 'from-a'
                type = 'jar'
            }
        }
    }
    artifacts {
        compile file('a.jar')
        compile file('a.jar')
    }
}
project(':b') {
    dependencies {
        compile files('b-lib.jar')
        compile files('b-lib.jar')
        compile 'org:test2:1.0'
        compile project(':c')
    }
    artifacts {
        compile file('b.jar')
    }
}
project(':c') {
    dependencies {
        compile files('c-lib.jar')
        compile 'org:test3:1.0'
        compile('org:test:1.0') {
            artifact {
                name = 'test'
                classifier = 'from-c'
                type = 'jar'
            }
            artifact {
                // this is the default artifact
                name = 'test'
                type = 'jar'
            }
        }
    }
    artifacts {
        compile file('c.jar')
    }
}

task show {
    doLast {
        println "artifacts 1: " + configurations.compile.incoming.artifacts.collect { it.file.name }
        println "artifacts 2: " + configurations.compile.incoming.artifactView { }.artifacts.collect { it.file.name }
        println "artifacts 3: " + configurations.compile.incoming.artifactView { lenient = true }.artifacts.collect { it.file.name }

        println "files 1: " + configurations.compile.incoming.files.collect { it.name }
        println "files 2: " + configurations.compile.files.collect { it.name }
        println "files 3: " + configurations.compile.resolve().collect { it.name }
        println "files 4: " + configurations.compile.incoming.artifactView { }.files.collect { it.name }
        println "files 5: " + configurations.compile.incoming.artifactView { lenient = true }.files.collect { it.name }

        println "files 6: " + configurations.compile.files { true }.collect { it.name }
        println "files 7: " + configurations.compile.fileCollection { true }.collect { it.name }
        println "files 8: " + configurations.compile.fileCollection { true }.files.collect { it.name }
        println "files 9: " + configurations.compile.incoming.artifactView { }.files.collect { it.name }
        println "files 10: " + configurations.compile.incoming.artifactView { lenient = true }.files.collect { it.name }
    }
}
"""

        when:
        run 'show'

        then:
        // local artifacts are not de-duplicated. This is documenting existing behaviour rather than desired behaviour
        outputContains("artifacts 1: [test-lib.jar, a.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("artifacts 2: [test-lib.jar, a.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("artifacts 3: [test-lib.jar, a.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")

        outputContains("files 1: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 2: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 3: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 4: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 5: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")

        // the filtered views order files differently. This is documenting existing behaviour rather than desired behaviour
        outputContains("files 6: [test-lib.jar, a.jar, a-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, c.jar, c-lib.jar, test3-1.0.jar, b.jar, b-lib.jar, test2-1.0.jar]")
        outputContains("files 7: [test-lib.jar, a.jar, a-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, c.jar, c-lib.jar, test3-1.0.jar, b.jar, b-lib.jar, test2-1.0.jar]")
        outputContains("files 8: [test-lib.jar, a.jar, a-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, c.jar, c-lib.jar, test3-1.0.jar, b.jar, b-lib.jar, test2-1.0.jar]")
        outputContains("files 9: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
        outputContains("files 10: [test-lib.jar, a.jar, a-lib.jar, b.jar, b-lib.jar, c.jar, c-lib.jar, test-1.0.jar, test-1.0-from-main.jar, test-1.0-from-a.jar, test-1.0-from-c.jar, test2-1.0.jar, test3-1.0.jar]")
    }
}
