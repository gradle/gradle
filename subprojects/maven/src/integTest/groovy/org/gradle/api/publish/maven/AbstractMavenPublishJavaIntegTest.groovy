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

package org.gradle.api.publish.maven

import org.gradle.api.attributes.Category
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenDependencyExclusion
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenJavaModule
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Ignore
import spock.lang.Issue

abstract class AbstractMavenPublishJavaIntegTest extends AbstractMavenPublishIntegTest {
    MavenFileModule module = mavenRepo.module("org.gradle.test", "publishTest", "1.9")
    MavenJavaModule javaLibrary = javaLibrary(module, features(), withDocs())

    abstract boolean withDocs()

    abstract List<String> features()

    def "can publish java-library with no dependencies"() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.assertNoDependencies()

        and:
        resolveArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveApiArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
        resolveRuntimeArtifacts(javaLibrary) { expectFiles "publishTest-1.9.jar" }
    }

    def "can publish java-library with dependencies"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "baz", "1.0+10")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "qux", "1.0-latest")).withModuleMetadata().publish()

        createBuildScripts("""

            dependencies {
                api "org.test:foo:1.0"
                implementation "org.test:bar:1.0"
                implementation "org.test:baz:1.0+10"
                implementation "org.test:qux:1.0-latest"
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            ${mavenTestRepository()}
        """)

        when:
        run "publish"

        then:
        outputDoesNotContain(DefaultMavenPublication.INCOMPATIBLE_FEATURE)
        javaLibrary.assertPublished()
        javaLibrary.assertApiDependencies("org.test:foo:1.0")
        javaLibrary.assertRuntimeDependencies("org.test:bar:1.0", "org.test:baz:1.0+10", "org.test:qux:1.0-latest")

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.0.jar", "baz-1.0+10.jar", "qux-1.0-latest.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }

        and:
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
            withoutModuleMetadata {
                expectFiles "foo-1.0.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.0.jar", "baz-1.0+10.jar", "qux-1.0-latest.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }
    }

    def "can publish java-library with dependencies and excludes"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                api "commons-collections:commons-collections:3.2.2"
                compileOnly "javax.servlet:servlet-api:2.5"
                runtimeOnly "commons-io:commons-io:1.4"
                testImplementation "junit:junit:4.13"
                api ("org.springframework:spring-core:2.5.6") {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                api ("commons-beanutils:commons-beanutils:1.8.3") {
                   exclude group : 'commons-logging'
                }
                api ("commons-dbcp:commons-dbcp:1.4") {
                   transitive = false
                }
                api ("org.apache.camel:camel-jackson:2.15.3") {
                   exclude module : 'camel-core'
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["compile", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("commons-collections:commons-collections:3.2.2", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-io:commons-io:1.4")
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.springframework:spring-core:2.5.6", new MavenDependencyExclusion("commons-logging", "commons-logging"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("commons-beanutils:commons-beanutils:1.8.3", new MavenDependencyExclusion("commons-logging", "*"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("commons-dbcp:commons-dbcp:1.4", new MavenDependencyExclusion("*", "*"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.apache.camel:camel-jackson:2.15.3", new MavenDependencyExclusion("*", "camel-core"))

        and:
        javaLibrary.assertApiDependencies("commons-collections:commons-collections:3.2.2", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        javaLibrary.assertRuntimeDependencies("commons-io:commons-io:1.4")
        def apiVariant = javaLibrary.parsedModuleMetadata.variant("apiElements")
        apiVariant.dependencies.find { it.coords == 'org.springframework:spring-core:2.5.6' }.excludes == ['commons-logging:commons-logging']
        apiVariant.dependencies.find { it.coords == 'commons-beanutils:commons-beanutils:1.8.3' }.excludes == ['commons-logging:*']
        apiVariant.dependencies.find { it.coords == 'commons-dbcp:commons-dbcp:1.4' }.excludes == ['*:*']
        apiVariant.dependencies.find { it.coords == 'org.apache.camel:camel-jackson:2.15.3' }.excludes == ['*:camel-core']

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "camel-jackson-2.15.3.jar", "commons-beanutils-1.8.3.jar", "commons-collections-3.2.2.jar", "commons-dbcp-1.4.jar", "commons-io-1.4.jar",
                "jackson-annotations-2.4.0.jar", "jackson-core-2.4.3.jar", "jackson-databind-2.4.3.jar", "jackson-module-jaxb-annotations-2.4.3.jar",
                "publishTest-1.9.jar", "spring-core-2.5.6.jar"
        }
    }

    def "can publish java-library with strict dependencies"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                api "org.springframework:spring-core:2.5.6"
                implementation("commons-collections:commons-collections") {
                    version { strictly '3.2.2' }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["compile", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("org.springframework:spring-core:2.5.6")
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:3.2.2")

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                strictly(null)
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('commons-collections:commons-collections:3.2.2') {
                noMoreExcludes()
                prefers(null)
                strictly('3.2.2')
                rejects()
            }
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                prefers(null)
                strictly(null)
                rejects()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles 'commons-collections-3.2.2.jar', 'commons-logging-1.1.1.jar', 'publishTest-1.9.jar', 'spring-core-2.5.6.jar'
        }
    }

    def "can publish java-library with dependency constraints"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                api "org.springframework:spring-core:1.2.9"
                implementation "org.apache.commons:commons-compress:1.5"
                constraints {
                    api "commons-logging:commons-logging:1.1"
                    implementation "commons-logging:commons-logging:1.2"
                    implementation("org.tukaani:xz") {
                        version { strictly "1.6" }
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"
        module.removeGradleMetadataRedirection()

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.packaging == null // 'jar' packaging
        javaLibrary.parsedPom.scopes.keySet() == ["compile", "no_scope", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("org.springframework:spring-core:1.2.9")

        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("org.apache.commons:commons-compress:1.5")
        javaLibrary.parsedPom.scopes.no_scope.assertDependencyManagement("commons-logging:commons-logging:1.1", "commons-logging:commons-logging:1.2", "org.tukaani:xz:1.6")

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency('org.springframework:spring-core:1.2.9') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-logging:commons-logging:1.1') { rejects() }

            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('org.springframework:spring-core:1.2.9') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-logging:commons-logging:1.1') { rejects() }
            constraint('commons-logging:commons-logging:1.2') { rejects() }

            dependency('org.apache.commons:commons-compress:1.5') {
                rejects()
                noMoreExcludes()
            }
            constraint('org.tukaani:xz:1.6') {
                prefers(null)
                strictly('1.6')
                rejects()
            }

            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles 'commons-compress-1.5.jar', 'commons-logging-1.2.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.6.jar'
            }
            withoutModuleMetadata {
                // POM <dependencyManagement> elements are not consumed as constraints for packaging == 'jar'.
                expectFiles 'commons-compress-1.5.jar', 'commons-logging-1.0.4.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.2.jar'
            }
        }
    }

    def "can publish java-library with rejected versions"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                constraints {
                    api("commons-logging:commons-logging") {
                        version {
                            rejectAll()
                        }
                    }
                }
                implementation("commons-collections:commons-collections:[3.2, 4)") {
                    version {
                        reject '3.2.1', '[3.2.2,)'
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"
        module.removeGradleMetadataRedirection()

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set

        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:[3.2, 4)")
        javaLibrary.parsedPom.scopes.runtime.assertDependencyManagement()

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            constraint('commons-logging:commons-logging:') {
                rejects '+'
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('commons-collections:commons-collections:[3.2, 4)') {
                noMoreExcludes()
                rejects '3.2.1', '[3.2.2,)'
            }
            constraint('commons-logging:commons-logging:') {
                rejects '+'
            }
            noMoreDependencies()
        }

        then:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles 'commons-collections-3.2.jar', 'publishTest-1.9.jar'
            }
            withoutModuleMetadata {
                expectFiles 'commons-collections-3.2.2.jar', 'publishTest-1.9.jar'
            }
        }

    }

    def "can publish java-library with dependencies without version"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections"
                constraints {
                    implementation "commons-collections:commons-collections:3.2.2"
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["no_scope", "runtime"] as Set
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:")
        javaLibrary.parsedPom.scopes.no_scope.assertDependencyManagement("commons-collections:commons-collections:3.2.2")

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('commons-collections:commons-collections') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-collections:commons-collections:3.2.2') { rejects() }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles 'commons-collections-3.2.2.jar', 'publishTest-1.9.jar'
        }
    }

    def "can publish java-library with dependencies with maven incompatible version notation: #version"() {

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:$version"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputContains(DefaultMavenPublication.INCOMPATIBLE_FEATURE)
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:$version")

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("commons-collections:commons-collections:$version") {
                rejects()
                noMoreExcludes()
            }
            noMoreDependencies()
        }

        where:
        version << ['1.+', 'latest.milestone', '+']
    }

    def "can publish java-library with attached artifacts"() {
        given:
        createBuildScripts("""
            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                archiveClassifier = "source"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        artifact sourceJar
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.withClassifiedArtifact("source", "jar").assertPublished()

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "publishTest-1.9.jar"
        }

        and:
        resolveArtifacts(javaLibrary) {
            classifier = 'source'
            expectFiles "publishTest-1.9-source.jar"
        }
    }

    void "'#gradleConfiguration' dependencies end up in '#mavenScope' scope with '#plugin' plugin"() {
        given:
        createBuildScripts """
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            dependencies {
                $gradleConfiguration project(':b')
            }
        """, plugin
        settingsFile << '''
            include "b"
        '''

        file('b/build.gradle') << """
            apply plugin: 'java'

            group = 'org.gradle.test'
            version = '1.2'

        """

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublished()
        if (mavenScope == 'compile') {
            if (gradleConfiguration == 'compileOnlyApi') {
                assertCompileOnlyApiDependencies('org.gradle.test:b:1.2')
            } else {
                javaLibrary.assertApiDependencies('org.gradle.test:b:1.2')
            }
        } else {
            javaLibrary.assertRuntimeDependencies('org.gradle.test:b:1.2')
        }

        where:
        plugin         | gradleConfiguration | mavenScope
        'java'         | 'implementation'    | 'runtime'
        'java'         | 'runtimeOnly'       | 'runtime'

        'java-library' | 'api'               | 'compile'
        'java-library' | 'compileOnlyApi'    | 'compile'
        'java-library' | 'runtimeOnly'       | 'runtime'
        'java-library' | 'implementation'    | 'runtime'

    }

    void assertCompileOnlyApiDependencies(String... expected) {
        javaLibrary.features.each { feature ->
            javaLibrary.assertDependencies(feature, 'apiElements', 'compile', [], expected)
            expected.each {
                assert !javaLibrary.parsedModuleMetadata.variant('runtimeElements').dependencies*.coords.contains(it)
            }
        }
    }

    def "can publish java-library with capabilities"() {
        given:
        createBuildScripts("""
            configurations.api.outgoing.capability 'org:foo:1.0'
            configurations.implementation.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputContains '''
Maven publication 'maven' pom metadata warnings (silence with 'suppressPomMetadataWarningsFor(variant)'):
  - Variant apiElements:
      - Declares capability org:foo:1.0 which cannot be mapped to Maven'''
        outputContains '''
  - Variant runtimeElements:
      - Declares capability org:bar:1.0 which cannot be mapped to Maven
      - Declares capability org:foo:1.0 which cannot be mapped to Maven'''

        for (def feature : features().findAll { it != MavenJavaModule.MAIN_FEATURE }) {
            outputContains """
  - Variant ${feature}SourceSetApiElements:
      - Declares capability org.gradle.test:publishTest-${feature}:1.9 which cannot be mapped to Maven"""
        }

        javaLibrary.assertPublished()

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            capability('org', 'foo', '1.0')
            noMoreCapabilities()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            capability('org', 'foo', '1.0')
            capability('org', 'bar', '1.0')
            noMoreCapabilities()
        }
    }

    def "can publish java-library with capability requests"() {
        given:
        createBuildScripts("""
            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }
            ${withDocs() ? """tasks.javadoc {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }
            """ : ""}
            dependencies {
                implementation("org.test:foo:1.0") {
                    capabilities {
                        requireCapability("org.test:foo-feature2")
                    }
                }
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        for (def feature : features().findAll { it != MavenJavaModule.MAIN_FEATURE }) {
            outputContains """
  - Variant ${feature}SourceSetApiElements:
      - Declares capability org.gradle.test:publishTest-${feature}:1.9 which cannot be mapped to Maven"""
        }

        javaLibrary.assertPublished()

        and:
        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.test:foo:1.0") {
                hasRequestedCapability("org.test", "foo-feature2")
                noMoreCapabilities()
            }
        }
    }

    def "does not warn for the default capability if it was declared explicitly"() {
        given:
        createBuildScripts("""
            configurations.api.outgoing.capability 'org.gradle.test:publishTest:1.9'
            configurations.implementation.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputContains '''
  - Variant runtimeElements:
      - Declares capability org:bar:1.0 which cannot be mapped to Maven
'''
        for (def feature : features().findAll { it != MavenJavaModule.MAIN_FEATURE }) {
            outputContains """
  - Variant ${feature}SourceSetApiElements:
      - Declares capability org.gradle.test:publishTest-${feature}:1.9 which cannot be mapped to Maven"""
        }

        javaLibrary.assertPublished()

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            capability('org.gradle.test', 'publishTest', '1.9')
            noMoreCapabilities()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            capability('org.gradle.test', 'publishTest', '1.9')
            capability('org', 'bar', '1.0')
            noMoreCapabilities()
        }
    }

    def "can ignore publication warnings"() {
        given:
        def silenceMethod = "suppressPomMetadataWarningsFor"
        createBuildScripts("""

            configurations.api.outgoing.capability 'org:foo:1.0'
            configurations.implementation.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        $silenceMethod('runtimeElements')
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputContains(DefaultMavenPublication.PUBLICATION_WARNING_FOOTER)
        outputContains("$silenceMethod(variant)")
        outputContains('Declares capability org:foo:1.0')
        outputContains("Variant apiElements")
        outputDoesNotContain("Variant runtimeElements")
        javaLibrary.assertPublished()
    }

    def "can ignore all publication warnings"() {
        given:
        createBuildScripts("""

            configurations.api.outgoing.capability 'org:foo:1.0'
            configurations.implementation.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        suppressAllPomMetadataWarnings()
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputDoesNotContain(DefaultMavenPublication.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()
    }

    @Issue("https://github.com/gradle/gradle/issues/5034, https://github.com/gradle/gradle/issues/5035")
    void "configuration exclusions are published in generated POM and Gradle metadata"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "a", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "b", "2.0")).withModuleMetadata().publish()
        createBuildScripts("""
            configurations {
                api.exclude(group: "api-group", module: "api-module")
                apiElements.exclude(group: "apiElements-group", module: "apiElements-module")
                runtimeElements.exclude(group: "runtimeElements-group", module: "runtimeElements-module")
                implementation.exclude(group: "implementation-group", module: "implementation-module")
                runtimeOnly.exclude(group: "runtimeOnly-group", module: "runtimeOnly-module")
            }

            dependencies {
                api "org.test:a:1.0"
                implementation "org.test:b:2.0"
                api project(':subproject')
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            ${mavenTestRepository()}
        """)

        settingsFile << """
            include "subproject"
        """
        file('subproject/build.gradle') << """
            apply plugin: 'java'
            group = 'org.gradle.test'
            version = '1.2'
        """

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.assertApiDependencies("org.test:a:1.0", "org.gradle.test:subproject:1.2")
        javaLibrary.assertRuntimeDependencies("org.test:b:2.0")
        with(javaLibrary.parsedPom) {
            with(scopes.compile) {
                hasDependencyExclusion("org.test:a:1.0", new MavenDependencyExclusion("apiElements-group", "apiElements-module"))
                hasDependencyExclusion("org.test:a:1.0", new MavenDependencyExclusion("api-group", "api-module"))
                hasDependencyExclusion("org.gradle.test:subproject:1.2", new MavenDependencyExclusion("apiElements-group", "apiElements-module"))
                hasDependencyExclusion("org.gradle.test:subproject:1.2", new MavenDependencyExclusion("api-group", "api-module"))
            }
            with(scopes.runtime) {
                hasDependencyExclusion("org.test:b:2.0", new MavenDependencyExclusion("runtimeElements-group", "runtimeElements-module"))
                hasDependencyExclusion("org.test:b:2.0", new MavenDependencyExclusion("implementation-group", "implementation-module"))
                hasDependencyExclusion("org.test:b:2.0", new MavenDependencyExclusion("api-group", "api-module"))
                hasDependencyExclusion("org.test:b:2.0", new MavenDependencyExclusion("runtimeOnly-group", "runtimeOnly-module"))
            }
        }

        and:
        with(javaLibrary.parsedModuleMetadata) {
            variant("apiElements") {
                dependency('org.test:a:1.0') {
                    hasExclude('apiElements-group', 'apiElements-module')
                    hasExclude('api-group', 'api-module')
                    noMoreExcludes()
                }
                dependency('org.gradle.test:subproject:1.2') {
                    hasExclude('apiElements-group', 'apiElements-module')
                    hasExclude('api-group', 'api-module')
                    noMoreExcludes()
                }
            }
            variant("runtimeElements") {
                dependency('org.test:a:1.0') {
                    hasExclude('runtimeElements-group', 'runtimeElements-module')
                    hasExclude('implementation-group', 'implementation-module')
                    hasExclude('api-group', 'api-module')
                    hasExclude('runtimeOnly-group', 'runtimeOnly-module')
                    noMoreExcludes()
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache
    def "can publish java-library with dependencies/constraints with attributes"() {
        given:
        settingsFile << "include 'utils'\n"
        file("utils/build.gradle") << '''
            def attr1 = Attribute.of('custom', String)
            version = '1.0'
            configurations {
                one {
                    attributes.attribute(attr1, 'magnificent')
                }
                two {
                    attributes.attribute(attr1, 'bazinga')
                }
            }
        '''
        createBuildScripts("""
            def attr1 = Attribute.of('custom', String)
            def attr2 = Attribute.of('nice', Boolean)

            dependencies {
                api("org.test:bar:1.0") {
                    attributes {
                        attribute(attr1, 'hello')
                    }
                }

                api(project(':utils')) {
                    attributes {
                        attribute(attr1, 'bazinga')
                    }
                }

                constraints {
                    implementation("org.test:bar:1.1") {
                        attributes {
                            attribute(attr1, 'world')
                            attribute(attr2, true)
                        }
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        and:
        outputContains(DefaultMavenPublication.UNSUPPORTED_FEATURE)
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency('org.test:bar:1.0') {
                hasAttribute('custom', 'hello')
            }
            dependency('publishTest:utils:1.0') {
                hasAttribute('custom', 'bazinga')
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('org.test:bar:1.0') {
                hasAttribute('custom', 'hello')
            }
            dependency('publishTest:utils:1.0') {
                hasAttribute('custom', 'bazinga')
            }
            constraint('org.test:bar:1.1') {
                hasAttributes(custom: 'world', nice: true)
            }
            noMoreDependencies()
        }
    }

    @Issue("gradle/gradle#5450")
    def "doesn't fail with NPE if no component is attached to a publication"() {
        createBuildScripts("""
        publishing {
            publications {
                java(MavenPublication) {
                    artifact jar
                }
            }
        }
        """)

        when:
        run "generateMetadataFileForJavaPublication"

        then:
        skipped(':generateMetadataFileForJavaPublication')
        outputContains "Maven publication 'java' isn't attached to a component. Gradle metadata only supports publications with software components (e.g. from component.java)"
    }

    @ToBeFixedForConfigurationCache
    def 'can publish java library with a #config dependency on a published BOM platform"'() {
        given:
        javaLibrary(mavenRepo.module("org.test", "bom", "1.0")).hasPackaging('pom').dependencyConstraint(mavenRepo.module('org.test', 'bar', '1.1')).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                ${config} "org.test:bar"
                ${config} platform("org.test:bom:1.0")
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            ${mavenTestRepository()}
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        def mavenModule = javaLibrary.mavenModule
        mavenModule.parsedPom.scopes['import'].expectDependencyManagement('org.test:bom:1.0').hasType('pom')
        mavenModule.parsedPom.scopes[scope].assertDependsOn('org.test:bar:')

        and:
        if (config == "api") {
            javaLibrary.parsedModuleMetadata.variant("apiElements") {
                dependency('org.test:bar:').exists()
                dependency('org.test:bom:1.0') {
                    hasAttribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                noMoreDependencies()
            }
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('org.test:bar:').exists()
            dependency('org.test:bom:1.0') {
                hasAttribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }
            noMoreDependencies()
        }

        where:
        config           | scope
        "api"            | "compile"
        "implementation" | "runtime"

    }

    @ToBeImplemented("Currently we cannot detect that a platform is in fact a virtual one during publication")
    @Ignore
    def 'can publish a java library using a virtual platform by ignoring it'() {
        given:
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:bar:1.0"
                api platform("org.test:platform:1.0")
                components.withModule('org.test:bar', VirtualPlatform)
            }

            class VirtualPlatform implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        belongsTo("org.test:platform:\${id.version}")
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        def mavenModule = javaLibrary.mavenModule

        mavenModule.assertPublished()
        mavenModule.assertArtifactsPublished("publishTest-1.9.module", "publishTest-1.9.pom", "publishTest-1.9.jar")
        mavenModule.parsedPom.scopes['import'] == null

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            dependency('org.test:bar:1.0').exists()
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('org.test:bar:1.0').exists()
            noMoreDependencies()
        }

    }

    def 'can publish a java library using a virtual platform by ignoring it explicitly'() {
        given:
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""

            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
            }

            dependencies {
                api "org.test:bar:1.0"
                api platform("org.test:platform:1.0")
                components.withModule('org.test:bar', VirtualPlatform)
            }

            class VirtualPlatform implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        belongsTo("org.test:platform:\${id.version}")
                    }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        pom.withXml {
                            asNode().dependencyManagement.dependencies.dependency.findAll { node ->
                                node.groupId[0].text().equals('org.test') &&
                                node.artifactId[0].text().equals('platform') &&
                                node.scope[0].text().equals('import')
                            }.each { node -> node.replaceNode {} }
                        }
                    }
                }
            }

            ${mavenTestRepository()}
        """)


        when:
        run "publish"

        then:
        def mavenModule = javaLibrary.mavenModule

        mavenModule.assertPublished()
        javaLibrary.assertArtifactsPublished()
        mavenModule.parsedPom.scopes['import'] == null

        // Sadly this does not take care of the Gradle metadata
    }

    def 'can publish java library with a #config dependency on a java-platform subproject"'() {
        given:
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        settingsFile << """
include(':platform')
"""
        createBuildScripts("""
            dependencies {
                ${config} "org.test:bar"
                ${config} platform(project(':platform'))
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            project(':platform') {
                apply plugin: 'java-platform'

                group = 'org.gradle.test'
                version = '1.9'

                dependencies {
                    constraints {
                        api 'org.test:bar:1.0'
                    }
                }
            }
            ${mavenTestRepository()}
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        def mavenModule = javaLibrary.mavenModule
        mavenModule.parsedPom.scopes['import'].expectDependencyManagement('org.gradle.test:platform:1.9').hasType('pom')
        mavenModule.parsedPom.scopes[scope].assertDependsOn('org.test:bar:')

        and:
        if (config == "api") {
            javaLibrary.parsedModuleMetadata.variant("apiElements") {
                dependency('org.test:bar:').exists()
                dependency('org.gradle.test:platform:1.9') {
                    hasAttribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                noMoreDependencies()
            }
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency('org.test:bar:').exists()
            dependency('org.gradle.test:platform:1.9') {
                hasAttribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }
            noMoreDependencies()
        }

        where:
        config           | scope
        "api"            | "compile"
        "implementation" | "runtime"

    }

    def "publishes Gradle metadata redirection marker when Gradle metadata task is enabled (enabled=#enabled)"() {
        given:
        createBuildScripts("""
            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            generateMetadataFileForMavenPublication.enabled = $enabled
        """)
        settingsFile.text = "rootProject.name = 'publishTest' "

        when:
        succeeds 'publish'

        then:
        def module = javaLibrary.mavenModule
        module.hasGradleMetadataRedirectionMarker() == hasMarker

        where:
        enabled | hasMarker
        false   | false
        true    | true
    }

    def "fails when attempting to modify a published variant that does not exist"() {
        given:
        createBuildScripts """
            components.java.withVariantsFromConfiguration(configurations.annotationProcessor) {
                skip()
            }
        """

        when:
        fails "publish"

        then:
        failure.assertHasCause("Variant for configuration annotationProcessor does not exist in component java")
    }

    def "fails if trying to publish a component with all variants filtered"() {
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }


            ${features().collect { """
                components.java.withVariantsFromConfiguration(configurations.${MavenJavaModule.variantName(it, 'apiElements')}) { skip() }
                components.java.withVariantsFromConfiguration(configurations.${MavenJavaModule.variantName(it, 'runtimeElements')}) { skip() }
                if (${withDocs()}) {
                    components.java.withVariantsFromConfiguration(configurations.${MavenJavaModule.variantName(it, 'javadocElements')}) { skip() }
                    components.java.withVariantsFromConfiguration(configurations.${MavenJavaModule.variantName(it, 'sourcesElements')}) { skip() }
                }
            """ }.join('')}
        """)

        when:
        fails "publish"

        then:
        failure.assertHasCause("""Invalid publication 'maven':
  - This publication must publish at least one variant""")
    }

    def createBuildScripts(def append, String plugin = 'java-library') {
        settingsFile << "rootProject.name = 'publishTest'"

        buildFile << """
            plugins {
                id('maven-publish')
                id('$plugin')
            }
            sourceSets {
                ${features().findAll { it != MavenJavaModule.MAIN_FEATURE }.collect { "${it}SourceSet" }.join('\n')}
            }

            java {
                ${features().findAll { it != MavenJavaModule.MAIN_FEATURE }.collect { "registerFeature('$it') { usingSourceSet(sourceSets.${it}SourceSet); ${withDocs() ? 'withJavadocJar(); withSourcesJar()' : ''} }" }.join('\n')}
                ${withDocs() ? 'withJavadocJar(); withSourcesJar()' : ''}
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            group = 'org.gradle.test'
            version = '1.9'

            $append
        """
    }
}
