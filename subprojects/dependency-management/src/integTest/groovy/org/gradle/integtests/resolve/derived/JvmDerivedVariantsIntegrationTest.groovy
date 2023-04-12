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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

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

dependencies {
    implementation 'com.example:test:1.0'
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

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "sources jar attributes match derived variants attributes"() {
        file("consumer/build.gradle") << """
task resolve {
    dependsOn configurations.runtimeClasspath
    doLast {
        def artifacts = configurations.runtimeClasspath.incoming.artifactView {
            withVariantReselection()
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
            }
        }.artifacts
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

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "javadoc jar attributes match derived variants attributes"() {
        file("consumer/build.gradle") << """
task resolve {
    dependsOn configurations.runtimeClasspath
    doLast {
        def artifacts = configurations.runtimeClasspath.incoming.artifactView {
            withVariantReselection()
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.JAVADOC))
            }
        }.artifacts
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
