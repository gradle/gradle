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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class JavaLibraryOutgoingVariantsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        def repo = mavenRepo
        repo.module("test", "api", "1.0").publish()
        repo.module("test", "compile", "1.0").publish()
        repo.module("test", "compile-only", "1.0").publish()
        repo.module("test", "compile-only-api", "1.0").publish()
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
    apply plugin: 'java-library'
    dependencies {
        api 'test:api:1.0'
        implementation 'test:compile:1.0'
        implementation project(':other-java')
        implementation files('file-dep.jar')
        compileOnly 'test:compile-only:1.0'
        compileOnlyApi 'test:compile-only-api:1.0'
        runtimeOnly 'test:runtime:1.0'
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

    private resolve() {
        succeeds "resolve"
    }

    def "provides runtime JAR as default variant"() {
        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        outputContains("files: [java.jar, file-dep.jar, api-1.0.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.status=release}")
        outputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        outputContains("files: [java.jar, file-dep.jar, api-1.0.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}")
        outputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
    }

    def "provides API variant"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
                dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.UsageCompatibilityRules)
                dependencies.attributesSchema.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.LibraryElementsCompatibilityRules)
            }
"""
        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        outputContains("files: [main, api-1.0.jar, compile-only-api-1.0.jar]")
        outputContains("main (project :java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-api}")
        outputContains("api-1.0.jar (test:api:1.0) {artifactType=jar, org.gradle.status=release}")
        outputContains("compile-only-api-1.0.jar (test:compile-only-api:1.0) {artifactType=jar, org.gradle.status=release}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        outputContains("files: [main, api-1.0.jar, compile-only-api-1.0.jar]")
        outputContains("main (project :java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-api}")
        outputContains("api-1.0.jar (test:api:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-api}")
        outputContains("compile-only-api-1.0.jar (test:compile-only-api:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-api}")
    }

    @Unroll
    def "provides runtime variant - format: #format"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                if ($format) {
                    configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                }
                dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.UsageCompatibilityRules)
                dependencies.attributesSchema.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.LibraryElementsCompatibilityRules)
            }
"""
        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        outputContains("files: [java.jar, file-dep.jar, api-1.0.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.status=release}")
        outputContains("java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        outputContains("files: [java.jar, file-dep.jar, api-1.0.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}")
        outputContains("java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")

        where:
        format | _
        true   | _
        false  | _
    }

    def "provides runtime JAR variant using artifactType"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE)
            }
"""
        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        outputContains("files: [java.jar, file-dep.jar, api-1.0.jar, compile-1.0.jar, other-java.jar, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar}")
        outputContains("java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
    }

    def "provides runtime classes variant"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
                dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.UsageCompatibilityRules)
                dependencies.attributesSchema.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.LibraryElementsCompatibilityRules)
            }
"""

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        outputContains("files: [main, file-dep.jar, api-1.0.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.status=release}")
        outputContains("main (project :other-java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-runtime}")
        outputContains("main (project :java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-runtime}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        outputContains("files: [main, file-dep.jar, api-1.0.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}")
        outputContains("main (project :other-java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-runtime}")
        outputContains("main (project :java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-runtime}")
    }

    def "provides runtime resources variant"() {
        buildFile << """
            project(':consumer') {
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.RESOURCES))
                dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.UsageCompatibilityRules)
                dependencies.attributesSchema.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).compatibilityRules.add(org.gradle.api.internal.artifacts.JavaEcosystemSupport.LibraryElementsCompatibilityRules)
            }
"""

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:processResources", ":java:processResources", ":consumer:resolve")
        outputContains("files: [main, file-dep.jar, api-1.0.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.status=release}")
        outputContains("main (project :other-java) {artifactType=java-resources-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=resources, org.gradle.usage=java-runtime}")
        outputContains("main (project :java) {artifactType=java-resources-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=resources, org.gradle.usage=java-runtime}")

        when:
        buildFile << """
            // Currently presents different variant attributes when using the java-base plugin
            project(':consumer') {
                apply plugin: 'java-base'
            }
"""

        resolve()

        then:
        result.assertTasksExecuted(":other-java:processResources", ":java:processResources", ":consumer:resolve")
        outputContains("files: [main, file-dep.jar, api-1.0.jar, compile-1.0.jar, main, implementation-1.0.jar, runtime-1.0.jar, runtime-only-1.0.jar]")
        outputContains("file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}")
        outputContains("compile-1.0.jar (test:compile:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}")
        outputContains("main (project :other-java) {artifactType=java-resources-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=resources, org.gradle.usage=java-runtime}")
        outputContains("main (project :java) {artifactType=java-resources-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=resources, org.gradle.usage=java-runtime}")
    }

    static String defaultTargetPlatform() {
        "org.gradle.jvm.version=${JavaVersion.current().majorVersion}"
    }
}
