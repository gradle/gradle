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

import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenDependencyExclusion
import org.gradle.test.fixtures.maven.MavenJavaModule
import org.gradle.util.ToBeImplemented
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

class MavenPublishJavaIntegTest extends AbstractMavenPublishIntegTest {
    MavenJavaModule javaLibrary = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

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

        createBuildScripts("""
            dependencies {
                api "org.test:foo:1.0"
                implementation "org.test:bar:1.0"
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
        javaLibrary.assertApiDependencies("org.test:foo:1.0")
        javaLibrary.assertRuntimeDependencies("org.test:bar:1.0")

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.0.jar", "foo-1.0.jar", "publishTest-1.9.jar"
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
            expectFiles "bar-1.0.jar", "foo-1.0.jar", "publishTest-1.9.jar"
        }
    }

    def "can publish java-library with dependencies and excludes"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                compile "commons-collections:commons-collections:3.2.2"
                compileOnly "javax.servlet:servlet-api:2.5"
                runtime "commons-io:commons-io:1.4"
                testCompile "junit:junit:4.12"
                compile ("org.springframework:spring-core:2.5.6") {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                compile ("commons-beanutils:commons-beanutils:1.8.3") {
                   exclude group : 'commons-logging'
                }
                compile ("commons-dbcp:commons-dbcp:1.4") {
                   transitive = false
                }
                compile ("org.apache.camel:camel-jackson:2.15.3") {
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

        javaLibrary.parsedPom.scopes.keySet() == ["compile"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("commons-collections:commons-collections:3.2.2", "commons-io:commons-io:1.4", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.springframework:spring-core:2.5.6", new MavenDependencyExclusion("commons-logging", "commons-logging"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("commons-beanutils:commons-beanutils:1.8.3", new MavenDependencyExclusion("commons-logging", "*"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("commons-dbcp:commons-dbcp:1.4", new MavenDependencyExclusion("*", "*"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.apache.camel:camel-jackson:2.15.3", new MavenDependencyExclusion("*", "camel-core"))

        and:
        javaLibrary.assertApiDependencies("commons-collections:commons-collections:3.2.2", "commons-io:commons-io:1.4", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        def apiVariant = javaLibrary.parsedModuleMetadata.variant('api')
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

            ${jcenterRepository()}

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
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                strictly(null)
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:') {
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

            ${jcenterRepository()}

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

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.packaging == null // 'jar' packaging
        javaLibrary.parsedPom.scopes.keySet() == ["compile", "runtime"] as Set
        javaLibrary.parsedPom.scopes.compile.assertDependsOn("org.springframework:spring-core:1.2.9")
        javaLibrary.parsedPom.scopes.compile.assertDependencyManagement("commons-logging:commons-logging:1.1")

        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("org.apache.commons:commons-compress:1.5")
        javaLibrary.parsedPom.scopes.runtime.assertDependencyManagement("commons-logging:commons-logging:1.2", "org.tukaani:xz:1.6")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:1.2.9') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-logging:commons-logging:1.1') { rejects() }

            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
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
            constraint('org.tukaani:xz:') {
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

            ${jcenterRepository()}

            dependencies {
                constraints {
                    api("commons-logging:commons-logging") {
                        version {
                            rejectAll()
                        }
                    }
                }
                implementation("commons-collections:commons-collections") {
                    version { 
                        prefer '[3.2, 4)'
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

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set

        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:[3.2, 4)")
        javaLibrary.parsedPom.scopes.runtime.assertDependencyManagement()

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            constraint('commons-logging:commons-logging:') {
                rejects '+'
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:') {
                noMoreExcludes()
                prefers '[3.2, 4)'
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

            ${jcenterRepository()}

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

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:")
        javaLibrary.parsedPom.scopes.runtime.assertDependencyManagement("commons-collections:commons-collections:3.2.2")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
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

    def "can publish java-library with attached artifacts"() {
        given:
        createBuildScripts("""
            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                classifier "source"
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

    def "can publish java-library-platform with dependencies and constraints"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createBuildScripts("""
            dependencies {
                api "org.test:bar"
                implementation "org.test:foo:1.0"
                
                constraints {
                    api "org.test:bar:1.0"
                    implementation "org.test:bar:1.1"
                }
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaLibraryPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        def mavenModule = javaLibrary.mavenModule

        mavenModule.assertPublished()
        mavenModule.assertArtifactsPublished("publishTest-1.9.module", "publishTest-1.9.pom")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            files.empty
            dependency('org.test:bar:').exists()
            constraint('org.test:bar:1.0').exists()
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            files.empty
            dependency('org.test:bar:').exists()
            dependency('org.test:foo:1.0').exists()
            constraint('org.test:bar:1.0').exists()
            constraint('org.test:bar:1.1').exists()
            noMoreDependencies()
        }


        // Published with pom packaging
        assert javaLibrary.parsedPom.packaging == 'pom'
        javaLibrary.parsedPom.scopes.compile.assertDependsOn('org.test:bar:')
        javaLibrary.parsedPom.scopes.compile.assertDependencyManagement('org.test:bar:1.0')
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn('org.test:foo:1.0')
        javaLibrary.parsedPom.scopes.runtime.assertDependencyManagement('org.test:bar:1.1')

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar"
        }
        resolveApiArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles "bar-1.0.jar"
            }
            withoutModuleMetadata {
                // To allow this, we would need to support multiple dependency management entries with different scopes
                // for the same module. This is not supported by Maven and an attempt to implement it in Gradle failed.
                // See: https://github.com/gradle/gradle/issues/4202
                expectFiles "bar-1.1.jar"
            }
        }
        resolveRuntimeArtifacts(javaLibrary) {
            expectFiles "bar-1.1.jar", "foo-1.0.jar"
        }
    }

    @Unroll("'#gradleConfiguration' dependencies end up in '#mavenScope' scope with '#plugin' plugin")
    void "maps dependencies in the correct Maven scope"() {
        given:
        file("settings.gradle") << '''
            rootProject.name = 'publishTest' 
            include "b"
        '''
        buildFile << """
            apply plugin: "$plugin"
            apply plugin: "maven-publish"

            group = 'org.gradle.test'
            version = '1.9'

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
            
            dependencies {
                $gradleConfiguration project(':b')
            }
        """

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
            javaLibrary.assertApiDependencies('org.gradle.test:b:1.2')
        } else {
            javaLibrary.assertRuntimeDependencies('org.gradle.test:b:1.2')
        }

        where:
        plugin         | gradleConfiguration | mavenScope
        'java'         | 'compile'           | 'compile'
        'java'         | 'runtime'           | 'compile'
        'java'         | 'implementation'    | 'runtime'
        'java'         | 'runtimeOnly'       | 'runtime'

        'java-library' | 'api'               | 'compile'
        'java-library' | 'compile'           | 'compile'
        'java-library' | 'runtime'           | 'compile'
        'java-library' | 'runtimeOnly'       | 'runtime'
        'java-library' | 'implementation'    | 'runtime'

    }

    def "can publish java-library with capabilities"() {
        requiresExternalDependencies = true
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
        javaLibrary.assertPublished()

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            capability('org', 'foo', '1.0')
            noMoreCapabilities()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            capability('org', 'foo', '1.0')
            capability('org', 'bar', '1.0')
            noMoreCapabilities()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/5034, https://github.com/gradle/gradle/issues/5035")
    void "configuration exclusions are published in generated POM and Gradle metadata"() {
        given:
        createBuildScripts("""
            configurations.apiElements {
                exclude group: "foo", module: "bar"
            }

            configurations.runtimeElements {
                exclude group: "baz", module: "qux"
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
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.test:a:1.0", new MavenDependencyExclusion("foo", "bar"))
        javaLibrary.parsedPom.scopes.compile.hasDependencyExclusion("org.gradle.test:subproject:1.2", new MavenDependencyExclusion("foo", "bar"))
        javaLibrary.assertRuntimeDependencies("org.test:b:2.0")
        javaLibrary.parsedPom.scopes.runtime.hasDependencyExclusion("org.test:b:2.0", new MavenDependencyExclusion("baz", "qux"))

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.test:a:1.0') {
                hasExclude('foo', 'bar')
                noMoreExcludes()
            }
            dependency('org.gradle.test:subproject:1.2') {
                hasExclude('foo', 'bar')
                noMoreExcludes()
            }
        }
        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('org.test:a:1.0') {
                hasExclude('baz', 'qux')
                noMoreExcludes()
            }
        }
    }

    def "can publish java-library with dependencies/constraints with attributes"() {
        requiresExternalDependencies = true
        given:
        settingsFile << "include 'utils'\n"
        file("utils/build.gradle") << '''
            def attr1 = Attribute.of('custom', String)
            version = '1.0'
            configurations {
                one {
                    attributes.attribute(attr1, 'magnificient')
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
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.test:bar:1.0') {
                hasAttribute('custom', 'hello')
            }
            dependency('publishTest:utils:1.0') {
                hasAttribute('custom', 'bazinga')
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
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

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

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

    @Unroll
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
            javaLibrary.parsedModuleMetadata.variant('api') {
                dependency('org.test:bar:').exists()
                dependency('org.test:bom:1.0') {
                    hasAttribute(PlatformSupport.COMPONENT_CATEGORY.name, PlatformSupport.REGULAR_PLATFORM)
                }
                noMoreDependencies()
            }
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('org.test:bar:').exists()
            dependency('org.test:bom:1.0') {
                hasAttribute(PlatformSupport.COMPONENT_CATEGORY.name, PlatformSupport.REGULAR_PLATFORM)
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
                        from components.javaLibraryPlatform
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        def mavenModule = javaLibrary.mavenModule

        mavenModule.assertPublished()
        mavenModule.assertArtifactsPublished("publishTest-1.9.module", "publishTest-1.9.pom")
        mavenModule.parsedPom.scopes['import'].assertDependencyManagement([] as String[])

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.test:bar:1.0').exists()
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('org.test:bar:1.0').exists()
            noMoreDependencies()
        }

    }

}
