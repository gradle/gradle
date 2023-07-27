/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.resolve.derived

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JvmDerivedVariantsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
rootProject.name = 'test'
include 'consumer'
"""
        buildFile << """
plugins {
    id 'java'
    id 'maven-publish'
}

group = "com.example"
version = "1.0"

java {
    withJavadocJar()
    withSourcesJar()
}

java {
    sourceSets {
        foo
    }
    registerFeature("foo") {
        usingSourceSet(sourceSets.foo)
    }
}

publishing {
    repositories {
        mavenLocal()
    }
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
}
"""
        file("consumer/build.gradle") << """
plugins {
    id 'java'
}

repositories {
    mavenLocal()
}
"""
        file('src/main/java/com/example/Foo.java').java """
package com.example;

/**
* Foo class.
*/
public class Foo {

}
"""
        using m2
        succeeds("publishToMavenLocal")
    }

    def "sources jar attributes match derived variants attributes"() {
        file("consumer/build.gradle") << """
dependencies {
    implementation 'com.example:test:1.0'
}

task resolve {
    def artifacts = configurations.runtimeClasspath.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
        }
    }.artifacts
    doLast {
        assert artifacts.size() == 1
        artifacts[0].with {
            def attributes = variant.attributes
            def attributesAsMap = [:]
            attributes.keySet().each {
                attributesAsMap[it.name] = String.valueOf(attributes.getAttribute(it))
            }
            assert attributesAsMap.size() == 7
            assert attributesAsMap['org.gradle.category'] == 'documentation'
            assert attributesAsMap['org.gradle.dependency.bundling'] == 'external'
            assert attributesAsMap['org.gradle.docstype'] == 'sources'
            assert attributesAsMap['org.gradle.usage'] == 'java-runtime'
            assert attributesAsMap['org.gradle.status'] == 'release'
            assert attributesAsMap['org.gradle.libraryelements'] == 'jar'
            assert attributesAsMap['artifactType'] == 'jar'
        }
    }
}
"""
        when:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()

        when:
        removeGMM()
        and:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()
    }

    def "javadoc jar attributes match derived variants attributes"() {
        file("consumer/build.gradle") << """
dependencies {
    implementation 'com.example:test:1.0'
}

task resolve {
    def artifacts = configurations.runtimeClasspath.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.JAVADOC))
        }
    }.artifacts
    doLast {
        assert artifacts.size() == 1
        artifacts[0].with {
            def attributes = variant.attributes
            def attributesAsMap = [:]
            attributes.keySet().each {
                attributesAsMap[it.name] = String.valueOf(attributes.getAttribute(it))
            }
            assert attributesAsMap.size() == 7
            assert attributesAsMap['org.gradle.category'] == 'documentation'
            assert attributesAsMap['org.gradle.dependency.bundling'] == 'external'
            assert attributesAsMap['org.gradle.docstype'] == 'javadoc'
            assert attributesAsMap['org.gradle.usage'] == 'java-runtime'
            assert attributesAsMap['org.gradle.status'] == 'release'
            assert attributesAsMap['org.gradle.libraryelements'] == 'jar'
            assert attributesAsMap['artifactType'] == 'jar'
        }
    }
}
"""
        when:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()

        when:
        removeGMM()
        and:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()
    }

    def "exception thrown when re-selecting artifacts if dependency has explicit artifact"() {
        file("consumer/build.gradle") << """
dependencies {
    implementation 'com.example:test:1.0:foo'
}

task resolve {
    def reselected = configurations.runtimeClasspath.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
        }
    }.files
    doLast {
        println reselected.files
    }
}
"""
        when:
        fails(":consumer:resolve")
        then:
        failure.assertHasCause("Cannot reselect artifacts for variant runtimeElements of com.example:test:1.0 since it has an explicitly requested artifact. Use a lenient ArtifactView to silence this error.")

        when:
        removeGMM()
        and:
        fails(":consumer:resolve")
        then:
        failure.assertHasCause("Cannot reselect artifacts for variant runtime of com.example:test:1.0 since it has an explicitly requested artifact. Use a lenient ArtifactView to silence this error.")
    }

    def "can re-select artifacts if dependency has explicit artifact when lenient=true"() {
        file("consumer/build.gradle") << """
dependencies {
    implementation 'com.example:test'
    implementation 'com.example:test:1.0:foo'
}

task resolve {
    def normal = configurations.runtimeClasspath.incoming.files
    def reselected = configurations.runtimeClasspath.incoming.artifactView {
        withVariantReselection()
        lenient = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
        }
    }.files
    doLast {
        assert normal*.name == ["test-1.0.jar", "test-1.0-foo.jar"]
        assert reselected*.name == ["test-1.0-sources.jar"]
    }
}
"""
        when:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()

        when:
        removeGMM()
        and:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()
    }

    @NotYetImplemented // Currently we throw an exception since we can't decide between the production and foo sources
    def "variant reselection only re-selects among variants with the same capabilities"() {
        file("consumer/build.gradle") << """
dependencies {
    implementation('com.example:test:1.0') {
        capabilities {
            requireCapability("com.example:test-foo:1.0")
        }
    }
}

task resolve {
    def normal = configurations.runtimeClasspath.incoming.files
    def reselected = configurations.runtimeClasspath.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
        }
    }.files
    doLast {
        assert normal*.name == ["test-1.0-foo.jar"]
        assert reselected*.name == ["test-1.0-foo-sources.jar"]
    }
}
"""
        when:
        succeeds(":consumer:resolve")
        then:
        noExceptionThrown()
    }

    private void removeGMM() {
        m2.mavenRepo().module('com.example', 'test', '1.0').moduleMetadata.file.delete()
        file("consumer/build.gradle") << """
repositories {
    mavenLocal {
        metadataSources {
            mavenPom()
            // ignore GMM to force derivation
            assert !isGradleMetadataEnabled()
        }
    }
}
"""
    }
}
