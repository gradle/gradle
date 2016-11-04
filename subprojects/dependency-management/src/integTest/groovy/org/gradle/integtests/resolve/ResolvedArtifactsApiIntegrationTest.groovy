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
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ResolvedArtifactsApiIntegrationTest extends AbstractDependencyResolutionTest {
    def "result includes artifacts from local and external components and file dependencies"() {
        def module1 = mavenRepo.module("org", "test", "1.0").publish()
        def module2 = mavenRepo.module("org", "test2", "1.0").publish()

        settingsFile << """
include 'a', 'b'
rootProject.name = 'test'
"""
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
    configurations { 
        compile {
            attribute('usage', 'compile')
        }
    }
}
dependencies {
    compile files('test-lib.jar')    
    compile project(':a')
    compile 'org:test:1.0'
    artifacts {
        compile file('test.jar')
    }
}
project(':a') {
    dependencies {
        compile files('a-lib.jar')
        compile project(':b')
        compile 'org:test:1.0'
    }
    artifacts {
        compile file('a.jar')
    }
}
project(':b') {
    dependencies {
        compile files('b-lib.jar')
        compile 'org:test2:1.0'
    }
    artifacts {
        compile file('b.jar')
    }
}

task show {
    doLast {
        println "files: " + configurations.compile.incoming.artifacts.collect { it.file.name }
    }
}
"""

        when:
        run 'show'

        then:
        outputContains("files: [test-lib.jar, a-lib.jar, b-lib.jar, a.jar, test-1.0.jar, b.jar, test2-1.0.jar")
    }
}
