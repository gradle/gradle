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

class SelfResolvingDependencyIntegrationTest extends AbstractDependencyResolutionTest {
    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "can query file dependency for its files"() {
        buildFile << """
allprojects {
    configurations {
        compile
    }
}
artifacts {
    compile file("main.jar") // include to ensure artifacts are not included in the result
}
dependencies {
    compile files("lib.jar")
    compile "group:test1:1.0" // unknown module, include to ensure that other dependencies are not resolved
}

task verify {
    doLast {
        def dep = configurations.compile.dependencies.find { it instanceof FileCollectionDependency }
        println "files: " + dep.files.files.collect { it.name }
        println "resolve: " + dep.resolve().collect { it.name }
        println "resolve-not-transitive: " + dep.resolve(false).collect { it.name }
        println "resolve-transitive: " + dep.resolve(true).collect { it.name }
        dep.getBuildDependencies()
    }
}
"""

        when:
        executer.expectDocumentedDeprecationWarning("Directly resolving a file collection dependency's files has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and resolve the configuration. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        executer.expectDocumentedDeprecationWarning("Directly resolving a file collection dependency's files has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and resolve the configuration. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        executer.expectDocumentedDeprecationWarning("Directly resolving a file collection dependency's files has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and resolve the configuration. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        executer.expectDocumentedDeprecationWarning("Accessing the build dependencies of a file collection dependency has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration use the configuration to track task dependencies. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        run "verify"

        then:
        outputContains("files: [lib.jar]")
        outputContains("resolve: [lib.jar]")
        outputContains("resolve-not-transitive: [lib.jar]")
        outputContains("resolve-transitive: [lib.jar]")
    }

    // This test documents existing behaviour rather than desired behaviour
    @ToBeFixedForConfigurationCache(because = "Task uses the Configuration API")
    def "can query project dependency for its files"() {
        mavenRepo.module("group", "test1", "1.0").publish()
        mavenRepo.module("group", "test2", "1.0").publish()

        createDirs("child1", "child2", "child3")
        settingsFile << """
rootProject.name = "main"
include "child1", "child2", "child3"
"""
        buildFile << """
allprojects {
    repositories {
        maven { url '${mavenRepo.uri}' }
    }
    configurations {
        compile
        create('default') { extendsFrom compile }
    }
}
artifacts {
    compile file("main.jar")
}
dependencies {
    compile files("lib.jar")
    compile "group:test1:1.0"
    compile project(':child1')
    compile project(':child2')
}
project(':child1') {
    artifacts {
        compile file("child1.jar")
    }
    dependencies {
        compile files("child1-lib.jar")
        compile "group:test2:1.0"
        compile project(':child3')
    }
}
project(':child2') {
    artifacts {
        compile file("child2.jar")
    }
    dependencies {
        compile files("child2-lib.jar")
    }
}
project(':child3') {
    artifacts {
        compile file("child3.jar")
    }
    dependencies {
        compile files("child3-lib.jar")
    }
}

task verify {
    doLast {
        def dep = configurations.compile.dependencies.find { it instanceof ProjectDependency }
        println "files: " + dep.resolve().collect { it.name }
        println "files-not-transitive: " + dep.resolve(false).collect { it.name }
        println "files-transitive: " + dep.resolve(true).collect { it.name }
        dep.getBuildDependencies()
    }
}
"""

        when:
        executer.expectDocumentedDeprecationWarning("Directly resolving the files of project dependency ':child1' has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and resolve the configuration. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        executer.expectDocumentedDeprecationWarning("Directly resolving the files of project dependency ':child1' has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and resolve the configuration. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        executer.expectDocumentedDeprecationWarning("Directly resolving the files of project dependency ':child1' has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and resolve the configuration. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        executer.expectDocumentedDeprecationWarning("Accessing the build dependencies of project dependency ':child1' has been deprecated. This will fail with an error in Gradle 9.0. Add the dependency to a resolvable configuration and use the configuration to track task dependencies. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_self_resolving_dependency")
        run "verify"

        then:
        outputContains("files: [child1-lib.jar, child3-lib.jar]")
        outputContains("files-not-transitive: []")
        outputContains("files-transitive: [child1-lib.jar, child3-lib.jar]")
    }
}
