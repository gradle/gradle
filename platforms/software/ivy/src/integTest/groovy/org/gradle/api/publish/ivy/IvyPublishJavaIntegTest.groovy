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


package org.gradle.api.publish.ivy

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.publish.ivy.internal.publication.IvyComponentParser
import org.gradle.test.fixtures.ivy.IvyJavaModule
import spock.lang.Issue

class IvyPublishJavaIntegTest extends AbstractIvyPublishIntegTest {
    IvyJavaModule javaLibrary = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))

    String getDependencies() {
        """dependencies {
               api "commons-collections:commons-collections:3.2.2"
               compileOnly "javax.servlet:servlet-api:2.5"
               runtimeOnly "commons-io:commons-io:1.4"
               testImplementation "junit:junit:4.13"
           }
"""
    }

    void "can publish jar and descriptor to ivy repository"() {
        requiresExternalDependencies = true
        given:
        createBuildScripts("""
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            $dependencies
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublishedAsJavaModule()

        with(javaLibrary.parsedIvy) {
            configurations.keySet() == ["default", "compile", "runtime"] as Set
            configurations["default"].extend == ["runtime"] as Set
            configurations["runtime"].extend == null

            expectArtifact("publishTest").hasAttributes("jar", "jar", ["compile", "runtime"])
        }
        javaLibrary.assertApiDependencies('commons-collections:commons-collections:3.2.2')
        javaLibrary.assertRuntimeDependencies('commons-collections:commons-collections:3.2.2', 'commons-io:commons-io:1.4')

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"
        }
    }

    void "'#gradleConfiguration' dependencies end up in '#ivyConfiguration' configuration with '#plugin' plugin"() {
        given:
        file("settings.gradle") << '''
            rootProject.name = 'publishTest'
            include "b"
        '''
        buildFile << """
            plugins {
                id("$plugin")
                id("ivy-publish")
            }

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
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
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()
        if (ivyConfiguration == 'compile') {
            javaLibrary.assertApiDependencies('org.gradle.test:b:1.2')
        }
        if (gradleConfiguration != 'compileOnlyApi') {
            javaLibrary.assertRuntimeDependencies('org.gradle.test:b:1.2')
        }

        where:
        plugin         | gradleConfiguration | ivyConfiguration
        'java'         | 'implementation'    | 'runtime'
        'java'         | 'runtimeOnly'       | 'runtime'

        'java-library' | 'api'               | 'compile'
        'java-library' | 'compileOnlyApi'    | 'compile'
        'java-library' | 'runtimeOnly'       | 'runtime'
        'java-library' | 'implementation'    | 'runtime'

    }

    void "ignores extra artifacts added to configurations"() {
        given:
        createBuildScripts("""
            task extraJar(type: Jar) {
                from sourceSets.main.allJava
                archiveBaseName = "publishTest-extra"
            }

            artifacts {
                implementation extraJar
                archives extraJar
                it."default" extraJar
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublishedAsJavaModule()
    }

    void "can publish additional artifacts for java project"() {
        requiresExternalDependencies = true
        given:
        createBuildScripts("""
            $dependencies

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                archiveBaseName = "publishTest-source"
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        artifact(sourceJar) {
                            classifier "source"
                            type "sources"
                            conf "runtime"
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.withClassifiedArtifact('source', 'jar')
        javaLibrary.assertPublishedAsJavaModule()

        javaLibrary.parsedIvy.expectArtifact("publishTest", "jar", "source").hasAttributes("jar", "sources", ["runtime"], "source")

        and:
        javaLibrary.removeGradleMetadataRedirection()
        resolveArtifacts(javaLibrary) {
            withoutModuleMetadata {
                expectFiles "commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar", "publishTest-1.9-source.jar"
            }
            withModuleMetadata {
                // additional files are not published in Gradle metadata
                expectFiles "commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"
            }
        }

        and:
        resolveArtifacts(javaLibrary) {
            additionalArtifacts = javaLibrary.additionalArtifacts
            expectFiles "publishTest-1.9-source.jar"
        }
    }

    @Issue("GRADLE-3514")
    void "generated ivy descriptor includes dependency exclusions"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""
            $dependencies

            dependencies {
                api 'org.springframework:spring-core:2.5.6', {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                api "commons-beanutils:commons-beanutils:1.8.3", {
                    exclude group: 'commons-logging'
                }
                api "commons-dbcp:commons-dbcp:1.4", {
                    transitive = false
                }
                api "org.apache.camel:camel-jackson:2.15.3", {
                    exclude module: 'camel-core'
                }
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublishedAsJavaModule()

        def dep = javaLibrary.parsedIvy.expectDependency("org.springframework:spring-core:2.5.6")
        dep.exclusions.size() == 1
        dep.exclusions[0].org == 'commons-logging'
        dep.exclusions[0].module == 'commons-logging'

        javaLibrary.parsedIvy.dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasConf("compile->default")
        javaLibrary.parsedIvy.dependencies["commons-beanutils:commons-beanutils:1.8.3"].exclusions[0].org == 'commons-logging'
        !javaLibrary.parsedIvy.dependencies["commons-dbcp:commons-dbcp:1.4"].transitiveEnabled()
        javaLibrary.parsedIvy.dependencies["org.apache.camel:camel-jackson:2.15.3"].hasConf("compile->default")
        javaLibrary.parsedIvy.dependencies["org.apache.camel:camel-jackson:2.15.3"].exclusions[0].module == 'camel-core'

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            dependency('org.springframework:spring-core:2.5.6') {
                exists()
                hasExclude('commons-logging', 'commons-logging')
                noMoreExcludes()
            }
            dependency('commons-dbcp:commons-dbcp:1.4') {
                exists()
                notTransitive()
            }
            dependency('commons-beanutils', 'commons-beanutils', '1.8.3') {
                exists()
                hasExclude('commons-logging')
                noMoreExcludes()
            }
            dependency('org.apache.camel:camel-jackson:2.15.3') {
                exists()
                hasExclude('*', 'camel-core')
                noMoreExcludes()
            }
            dependency('commons-collections:commons-collections:3.2.2').exists()
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "camel-jackson-2.15.3.jar",
                "commons-beanutils-1.8.3.jar",
                "commons-collections-3.2.2.jar",
                "commons-dbcp-1.4.jar",
                "commons-io-1.4.jar",
                "jackson-annotations-2.4.0.jar",
                "jackson-core-2.4.3.jar",
                "jackson-databind-2.4.3.jar",
                "jackson-module-jaxb-annotations-2.4.3.jar",
                "publishTest-1.9.jar",
                "spring-core-2.5.6.jar"
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/4356, https://github.com/gradle/gradle/issues/5035")
    void "generated ivy descriptor includes configuration exclusions"() {
        def exclusion = { name -> "$name-group:$name-module" }
        def exclusions = { conf -> javaLibrary.parsedIvy.exclusions.findAll { it.conf == conf }.collect { it.org + ":" + it.module } }

        given:
        createBuildScripts("""
            configurations {
                api.exclude(group: "api-group", module: "api-module")
                apiElements.exclude(group: "apiElements-group", module: "apiElements-module")
                runtime.exclude(group: "runtime-group", module: "runtime-module")
                runtimeElements.exclude(group: "runtimeElements-group", module: "runtimeElements-module")
                implementation.exclude(group: "implementation-group", module: "implementation-module")
                runtimeOnly.exclude(group: "runtimeOnly-group", module: "runtimeOnly-module")
            }

            $dependencies

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        javaLibrary.assertPublishedAsJavaModule()
        exclusions('compile') == [exclusion("apiElements"), exclusion("api")]
        exclusions('runtime') == [exclusion("runtimeElements"), exclusion("implementation"), exclusion("api"), exclusion("runtimeOnly")]

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            dependency('commons-collections:commons-collections:3.2.2') {
                hasExclude('apiElements-group', 'apiElements-module')
                hasExclude('api-group', 'api-module')
                noMoreExcludes()
            }
        }
        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency('commons-io:commons-io:1.4') {
                hasExclude('runtimeElements-group', 'runtimeElements-module')
                hasExclude('implementation-group', 'implementation-module')
                hasExclude('api-group', 'api-module')
                hasExclude('runtimeOnly-group', 'runtimeOnly-module')
                noMoreExcludes()
            }
        }
    }

    void "defaultDependencies are included in published ivy descriptor"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            ${emptyJavaClasspath()}

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            ${mavenCentralRepository()}

            configurations.api.defaultDependencies { deps ->
                deps.add project.dependencies.create("org.test:default-dependency:1.1")
            }
"""

        when:
        succeeds "publish"

        then:
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublishedAsJavaModule()
        javaLibrary.assertApiDependencies("org.test:default-dependency:1.1")
    }

    void "dependency mutations are included in published ivy descriptor"() {
        given:
        settingsFile << "rootProject.name = 'publishTest'"

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            ${emptyJavaClasspath()}

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            dependencies {
                api "org.test:dep1:1.0"
            }

            configurations.api.withDependencies { deps ->
                deps.add project.dependencies.create("org.test:dep2:1.1")
            }
            configurations.api.withDependencies { deps ->
                deps.each { dep ->
                    dep.version { require 'X' }
                }
            }
"""

        when:
        succeeds "publish"

        then:
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublishedAsJavaModule()
        javaLibrary.assertApiDependencies('org.test:dep1:X', 'org.test:dep2:X')
    }

    def "can publish java-library with strict and prefer dependencies"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

            dependencies {
                api("org.springframework:spring-core") {
                    version { prefer '2.5.6' }
                }
                implementation("commons-collections:commons-collections") {
                    version { strictly '3.2.2' }
                }
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("org.springframework:spring-core:2.5.6@compile", "commons-collections:commons-collections:3.2.2@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            dependency('org.springframework:spring-core:') {
                noMoreExcludes()
                prefers('2.5.6')
                strictly(null)
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency('commons-collections:commons-collections:3.2.2') {
                noMoreExcludes()
                prefers(null)
                strictly('3.2.2')
                rejects()
            }
            dependency('org.springframework:spring-core:') {
                noMoreExcludes()
                prefers('2.5.6')
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
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains(IvyComponentParser.UNSUPPORTED_FEATURE)
        outputContains('commons-logging:commons-logging:1.1 declared as a dependency constraint')
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("org.springframework:spring-core:1.2.9@compile", "org.apache.commons:commons-compress:1.5@runtime")
        // we do not publish constraints to ivy

        and:
        javaLibrary.removeGradleMetadataRedirection()
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            dependency('org.springframework:spring-core:1.2.9') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-logging:commons-logging:1.1') { rejects() }

            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
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
                expectFiles 'commons-compress-1.5.jar', 'commons-logging-1.0.4.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.2.jar'
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
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        succeeds "publish"

        then:
        javaLibrary.removeGradleMetadataRedirection()
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains(IvyComponentParser.UNSUPPORTED_FEATURE)
        outputContains('commons-collections:commons-collections declared without version')
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency('commons-collections:commons-collections:') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-collections:commons-collections:3.2.2') { rejects() }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles 'commons-collections-3.2.2.jar', 'publishTest-1.9.jar'
            }
            withoutModuleMetadata {
                // Constraints cannot be published to Ivy files. Since we publish the _declared_ dependency
                // versions and not the resolved ones, this can't be resolved
                shouldFail {
                    assertHasCause 'Could not resolve all files for configuration'
                    assertHasCause 'Could not find commons-collections:commons-collections:.'
                }
            }
        }
    }

    def "can publish java-library with dependencies without version and using versionMapping"() {
        requiresExternalDependencies = true
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:3.2.+"
                constraints {
                    implementation "commons-collections:commons-collections:3.2.2"
                }
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
""")

        when:
        succeeds "publish"

        then:
        javaLibrary.removeGradleMetadataRedirection()
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains(IvyComponentParser.UNSUPPORTED_FEATURE)
        outputDoesNotContain('commons-collections:commons-collections declared without version')
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:3.2.2@runtime")
        javaLibrary.parsedIvy.dependencies["commons-collections:commons-collections:3.2.2"].revisionConstraint == "3.2.+"

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency('commons-collections:commons-collections:3.2.2') {
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

    def "'#requestedVersion' end up in '#expectedVersion' resolved version and '#requestedVersion' revConstraint"() {
        requiresExternalDependencies = true
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:${requestedVersion}"
                constraints {
                    implementation "commons-collections:commons-collections:${expectedVersion}"
                }
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
""")

        when:
        succeeds "publish"

        then:
        javaLibrary.removeGradleMetadataRedirection()
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains(IvyComponentParser.UNSUPPORTED_FEATURE)
        outputDoesNotContain('commons-collections:commons-collections declared without version')
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:${expectedVersion}@runtime")
        javaLibrary.parsedIvy.dependencies["commons-collections:commons-collections:${expectedVersion}"].revisionConstraint == requestedVersion

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency("commons-collections:commons-collections:${expectedVersion}") {
                rejects()
                noMoreExcludes()
            }
            constraint("commons-collections:commons-collections:${expectedVersion}") { rejects() }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "commons-collections-${expectedVersion}.jar", 'publishTest-1.9.jar'
        }

        where:
        requestedVersion | expectedVersion
        "3.2.+"          | "3.2.2"
        "[2.1.0,4.0.0)"  | "3.2.2"
        "latest.release" | "20040616"
    }

    def "can publish java-library with dependencies with version using versionMapping and not adding revConstraints"() {
        requiresExternalDependencies = true
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:3.2.2"
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        versionMapping {
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
""")

        when:
        succeeds "publish"

        then:
        javaLibrary.removeGradleMetadataRedirection()
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:3.2.2@runtime")
        !javaLibrary.parsedIvy.dependencies["commons-collections:commons-collections:3.2.2"].revisionConstraint

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency('commons-collections:commons-collections:3.2.2') {
                noMoreExcludes()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles 'commons-collections-3.2.2.jar', 'publishTest-1.9.jar'
        }
    }

    def "can publish java-library with rejected versions"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""

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
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.removeGradleMetadataRedirection()
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains(IvyComponentParser.UNSUPPORTED_FEATURE)
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:[3.2, 4)@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            constraint('commons-logging:commons-logging:') {
                rejects '+'
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            dependency('commons-collections:commons-collections:[3.2, 4)') {
                noMoreExcludes()
                rejects '3.2.1', '[3.2.2,)'
            }
            constraint('commons-logging:commons-logging:') {
                rejects '+'
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) {
            withModuleMetadata {
                expectFiles 'commons-collections-3.2.jar', 'publishTest-1.9.jar'
            }
            withoutModuleMetadata {
                expectFiles 'commons-collections-3.2.2.jar', 'publishTest-1.9.jar'
            }
        }
    }

    def "can publish java-library with capabilities"() {
        given:
        createBuildScripts("""

            configurations.apiElements.outgoing.capability 'org:foo:1.0'
            configurations.runtimeElements.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains('Declares capability org:foo:1.0')
        javaLibrary.assertPublished()

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            capability('org', 'foo', '1.0')
            noMoreCapabilities()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
            capability('org', 'bar', '1.0')
            noMoreCapabilities()
        }
    }

    def "can ignore publication warnings"() {
        given:
        def silenceMethod = "suppressIvyMetadataWarningsFor"
        createBuildScripts("""

            configurations.apiElements.outgoing.capability 'org:foo:1.0'
            configurations.runtimeElements.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        $silenceMethod('apiElements')
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains("$silenceMethod(variant)")
        outputContains('Declares capability org:bar:1.0')
        outputContains("Variant runtimeElements")
        outputDoesNotContain("Variant apiElements")
        javaLibrary.assertPublished()
    }

    def "can ignore all publication warnings by variant name"() {
        given:
        def silenceMethod = "suppressIvyMetadataWarningsFor"
        createBuildScripts("""

            configurations.apiElements.outgoing.capability 'org:foo:1.0'
            configurations.runtimeElements.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        $silenceMethod('apiElements')
                        $silenceMethod('runtimeElements')
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputDoesNotContain("Ivy publication 'ivy' warnings:")
        outputDoesNotContain('Declares capability org:foo:1.0')
        javaLibrary.assertPublished()
    }

    def "can ignore all publication warnings"() {
        given:
        createBuildScripts("""

            configurations.apiElements.outgoing.capability 'org:foo:1.0'
            configurations.runtimeElements.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        suppressAllIvyMetadataWarnings()
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputDoesNotContain(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputDoesNotContain("Ivy publication 'ivy' warnings:")
        outputDoesNotContain('Declares capability org:foo:1.0')
        javaLibrary.assertPublished()
    }

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

            ${emptyJavaClasspath()}

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        outputContains(IvyComponentParser.PUBLICATION_WARNING_FOOTER)
        outputContains(IvyComponentParser.UNSUPPORTED_FEATURE)
        javaLibrary.assertPublished()

        and:
        javaLibrary.parsedModuleMetadata.variant('apiElements') {
            dependency('org.test:bar:1.0') {
                hasAttribute('custom', 'hello')
            }
            dependency('publishTest:utils:1.0') {
                hasAttribute('custom', 'bazinga')
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtimeElements') {
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
                java(IvyPublication) {
                    artifact jar
                }
            }
        }
        """)

        when:
        run "generateMetadataFileForJavaPublication"

        then:
        skipped(':generateMetadataFileForJavaPublication')
        outputContains "Ivy publication 'java' isn't attached to a component. Gradle metadata only supports publications with software components (e.g. from component.java)"
    }

    def "publishes Gradle metadata redirection marker when Gradle metadata task is enabled (enabled=#enabled)"() {
        given:
        createBuildScripts("""
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            generateMetadataFileForIvyPublication.enabled = $enabled
        """)
        settingsFile.text = "rootProject.name = 'publishTest' "

        when:
        succeeds 'publish'

        then:
        def module = javaLibrary.backingModule
        module.hasGradleMetadataRedirectionMarker() == hasMarker

        where:
        enabled | hasMarker
        false   | false
        true    | true
    }

    def "can publish feature variants (optional: #optional)"() {
        given:
        createBuildScripts """
            ${optionalFeatureSetup()}
            dependencies {
                optionalFeatureImplementation 'org:foo:4.0'
            }
            components.java.addVariantsFromConfiguration(configurations.optionalFeatureRuntimeElements) {
                if ($optional) mapToOptional()
            }
            ${emptyJavaClasspath()}
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        with(javaLibrary.parsedIvy) {
            configurations.keySet() == ["default", "compile", "runtime", "optionalFeatureRuntimeElements"] as Set
            if (optional) {
                configurations["default"].extend == ["runtime"] as Set
            } else {
                configurations["default"].extend == ["runtime", "optionalFeatureRuntimeElements"] as Set
            }
            configurations["runtime"].extend == null
            configurations["optionalFeatureRuntimeElements"].extend == null

            expectArtifact("publishTest", "jar").hasConf(["compile", "runtime"])
            expectArtifact("publishTest", "jar", "optional-feature").hasConf(["optionalFeatureRuntimeElements"])
            assertConfigurationDependsOn("optionalFeatureRuntimeElements", "org:foo:4.0")
        }

        where:
        optional << [true, false]
    }

    def "an optional feature variant can repeat a dependency from a main variant"() {
        given:
        createBuildScripts("""
            ${optionalFeatureSetup()}
            dependencies {
                implementation 'org:foo:1.0'
                optionalFeatureImplementation 'org:foo:1.0'
            }
            components.java.addVariantsFromConfiguration(configurations.optionalFeatureRuntimeElements) {
                mapToOptional()
            }
            ${emptyJavaClasspath()}
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        succeeds "publish"

        then:
        with(javaLibrary.parsedIvy) {
            assertConfigurationDependsOn("optionalFeatureRuntimeElements", "org:foo:1.0")
            assertConfigurationDependsOn('runtime', "org:foo:1.0")
        }
    }

    def "a component's variant can be modified before publishing"() {
        given:
        createBuildScripts """
            dependencies {
                api 'org:foo:1.0'
                implementation 'org:bar:1.0'
            }
            ${emptyJavaClasspath()}
            components.java.withVariantsFromConfiguration(configurations.runtimeElements) {
                skip()
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        with(javaLibrary.parsedIvy) {
            assert configurations.keySet() == ["compile", "default"] as Set // skipped runtime
            assertConfigurationDependsOn('compile', "org:foo:1.0")
        }
        with(javaLibrary.parsedModuleMetadata) {
            assert variants.collect { it.name } == ["apiElements"]
            assert variants[0].dependencies.collect { it.toString() } == ["org:foo:1.0"]
        }
    }

    def "can not publish variant with attribute specifying category = verification"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            def testConf = configurations.create('testConf') {
                assert canBeResolved
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                mapToOptional()
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """)

        expect:
        fails('publish')
        failure.assertHasCause("Cannot publish module metadata for component 'java' which would include a variant 'testConf' that contains a 'org.gradle.category' attribute with a value of 'verification'.  " +
            "This attribute is reserved for test verification output and is not publishable.  " + variantAttributesLink())
    }

    private variantAttributesLink() {
        documentationRegistry.getDocumentationRecommendationFor("on this", "variant_attributes", "sec:verification_category")
    }

    def "can not publish variant with attribute specifying category = verification if defining new attribute with string"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            def testConf = configurations.create('testConf') {
                assert canBeResolved
                attributes.attribute(Attribute.of('org.gradle.category', String), 'verification')
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                mapToOptional()
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """)

        expect:
        fails('publish')
        failure.assertHasCause("Cannot publish module metadata for component 'java' which would include a variant 'testConf' that contains a 'org.gradle.category' attribute with a value of 'verification'.  " +
            "This attribute is reserved for test verification output and is not publishable.  " + new DocumentationRegistry().getDocumentationRecommendationFor("on this", "variant_attributes", "sec:verification_category"))
    }

    def "can not publish test results from java test suite"() {
        given:
        createBuildScripts("""
            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }

            def testResultsElementsForTest = configurations.testResultsElementsForTest
            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testResultsElementsForTest) {
                it.mapToMavenScope("runtime")
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """)

        file("src/test/java/com/example/SampleTest.java") << """
            package com.example;

            import org.junit.Test;

            public class SampleTest {
                @Test
                public void checkSomething() {
                    // pass
                }
            }""".stripIndent()

        expect:
        fails('test', 'publish')
        failure.assertHasCause("Cannot publish module metadata for component 'java' which would include a variant 'testResultsElementsForTest' that contains a 'org.gradle.category' attribute with a value of 'verification'.  " +
            "This attribute is reserved for test verification output and is not publishable.  " + new DocumentationRegistry().getDocumentationRecommendationFor("on this", "variant_attributes", "sec:verification_category"))
    }

    def "can publish variants with attribute specifying category if value not verification"() {
        given:
        createBuildScripts("""

            ${mavenCentralRepository()}

            def testConf = configurations.create('testConf') {
                assert canBeResolved
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, 'not verification'))
            }

            def javaComponent = components.findByName("java")
            javaComponent.addVariantsFromConfiguration(testConf) {
                mapToOptional()
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """)

        expect:
        succeeds('publish')
    }

    private void createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
            }

$append

            group = 'org.gradle.test'
            version = '1.9'

            ${mavenCentralRepository()}

"""
    }

    private static String optionalFeatureSetup() {
        """
            configurations {
                optionalFeatureImplementation
                optionalFeatureRuntimeElements {
                    extendsFrom optionalFeatureImplementation
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    }
                    outgoing.capability("org:optional-feature:\${version}")
                }
                compileClasspath.extendsFrom(optionalFeatureImplementation)
            }
            artifacts {
                optionalFeatureRuntimeElements file:file("\$buildDir/other-artifact.jar"), builtBy: 'touchFile', classifier: 'optional-feature'
            }
            task touchFile {
                // explicit dependency otherwise this task may run before the Jar task
                dependsOn tasks.jar
                def file = file("\$buildDir/other-artifact.jar")
                doLast {
                    file << "test"
                }
            }
        """
    }
}
