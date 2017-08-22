/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class JavaApplicationOutgoingVariantsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        def repo = mavenRepo
        repo.module("test", "compile", "1.0").publish()
        repo.module("test", "compile-only", "1.0").publish()
        repo.module("test", "runtime", "1.0").publish()
        repo.module("test", "implementation", "1.0").publish()
        repo.module("test", "runtime-only", "1.0").publish()

        settingsFile << "include 'other-java', 'java', 'consumer'"
        buildFile << """
def artifactType = Attribute.of('artifactType', String)

allprojects {
    repositories { maven { url '${mavenRepo.uri}' } }
}

project(':other-java') {
    apply plugin: 'java-library'
}

project(':java') {
    apply plugin: 'application'
    dependencies {
        compile 'test:compile:1.0'
        compile project(':other-java')
        compile files('file-dep.jar')
        compileOnly 'test:compile-only:1.0'
        runtime 'test:runtime:1.0'
        implementation 'test:implementation:1.0'
        runtimeOnly 'test:runtime-only:1.0'
    }
}

project(':consumer') {
    configurations { consume }
    dependencies { consume project(':java') }
    task resolve {
        inputs.files configurations.consume
        doLast {
            println "files: " + configurations.consume.files.collect { it.name }
            configurations.consume.incoming.artifacts.each {
                println "\$it.id \$it.variant.attributes"
            }
        }
    }
}
"""
    }

    def "provides runtime JAR as default variant"() {
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar}")
        result.assertOutputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
    }

    @Unroll
    def "provides API variant - #usage"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, ${usage})
            }
"""

        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, main, runtime-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar}")
        result.assertOutputContains("main (project :other-java) {artifactType=java-classes-directory, org.gradle.api.attributes.Usage=java-api-classes}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-api}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, main, runtime-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("main (project :other-java) {artifactType=java-classes-directory, org.gradle.api.attributes.Usage=java-api-classes}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-api}")

        where:
        usage                                          | _
        "objects.named(Usage, Usage.JAVA_API)"         | _
        "objects.named(Usage, Usage.JAVA_API_CLASSES)" | _
    }

    @Unroll
    def "provides runtime variant - #usage"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, ${usage})
            }
"""
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar}")
        result.assertOutputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")

        where:
        usage                                           | _
        "objects.named(Usage, Usage.JAVA_RUNTIME)"      | _
        "objects.named(Usage, Usage.JAVA_RUNTIME_JARS)" | _
    }

    def "provides runtime JAR variant using artifactType attribute"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE)
            }
"""
        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        result.assertOutputContains("files: [java.jar, file-dep.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar}")
        result.assertOutputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("java.jar (project :java) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
    }

    def "provides runtime classes variant"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME_CLASSES))
            }
"""

        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        result.assertOutputContains("files: [main, file-dep.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar}")
        result.assertOutputContains("main (project :other-java) {artifactType=java-classes-directory, org.gradle.api.attributes.Usage=java-runtime-classes}")
        result.assertOutputContains("main (project :java) {artifactType=java-classes-directory, org.gradle.api.attributes.Usage=java-runtime-classes}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        result.assertOutputContains("files: [main, file-dep.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("main (project :other-java) {artifactType=java-classes-directory, org.gradle.api.attributes.Usage=java-runtime-classes}")
        result.assertOutputContains("main (project :java) {artifactType=java-classes-directory, org.gradle.api.attributes.Usage=java-runtime-classes}")
    }

    def "provides runtime resources variant"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME_RESOURCES))
            }
"""

        when:
        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:processResources", ":java:processResources", ":consumer:resolve")
        result.assertOutputContains("files: [main, file-dep.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar}")
        result.assertOutputContains("main (project :other-java) {artifactType=java-resources-directory, org.gradle.api.attributes.Usage=java-runtime-resources}")
        result.assertOutputContains("main (project :java) {artifactType=java-resources-directory, org.gradle.api.attributes.Usage=java-runtime-resources}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        run "resolve"

        then:
        result.assertTasksExecuted(":other-java:processResources", ":java:processResources", ":consumer:resolve")
        result.assertOutputContains("files: [main, file-dep.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        result.assertOutputContains("file-dep.jar {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("compile.jar (test:compile:1.0) {artifactType=jar, org.gradle.api.attributes.Usage=java-runtime-jars}")
        result.assertOutputContains("main (project :other-java) {artifactType=java-resources-directory, org.gradle.api.attributes.Usage=java-runtime-resources}")
        result.assertOutputContains("main (project :java) {artifactType=java-resources-directory, org.gradle.api.attributes.Usage=java-runtime-resources}")
    }
}
