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

import spock.lang.Issue
import spock.lang.Unroll

class IvyPublishJavaIntegTest extends AbstractIvyPublishIntegTest {
    def javaLibrary = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))

    String getDependencies() {
        """dependencies {
                api "commons-collections:commons-collections:3.2.2"
                compileOnly "javax.servlet:servlet-api:2.5"
                runtimeOnly "commons-io:commons-io:1.4"
                testImplementation "junit:junit:4.12"
            }
"""
    }

    void "can publish jar and descriptor to ivy repository"() {
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
            configurations["default"].extend == ["runtime", "compile"] as Set
            configurations["runtime"].extend == null

            expectArtifact("publishTest").hasAttributes("jar", "jar", ["compile"])
        }
        javaLibrary.assertApiDependencies('commons-collections:commons-collections:3.2.2')
        javaLibrary.assertRuntimeDependencies('commons-io:commons-io:1.4')

        and:
        resolveArtifacts(javaLibrary) == ["commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"]

    }

    @Unroll("'#gradleConfiguration' dependencies end up in '#ivyConfiguration' configuration with '#plugin' plugin")
    void "maps dependencies in the correct Ivy configuration"() {
        given:
        file("settings.gradle") << '''
            rootProject.name = 'publishTest' 
            include "b"
        '''
        buildFile << """
            apply plugin: "$plugin"
            apply plugin: "ivy-publish"

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
        javaLibrary.assertPublished()
        if (ivyConfiguration == 'compile') {
            javaLibrary.assertApiDependencies('org.gradle.test:b:1.2')
        } else {
            javaLibrary.assertRuntimeDependencies('org.gradle.test:b:1.2')
        }

        where:
        plugin         | gradleConfiguration | ivyConfiguration
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

    public void "ignores extra artifacts added to configurations"() {
        given:
        createBuildScripts("""
            task extraJar(type: Jar) {
                from sourceSets.main.allJava
                baseName "publishTest-extra"
            }

            artifacts {
                runtime extraJar
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
        given:
        createBuildScripts("""
            $dependencies

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                baseName "publishTest-source"
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
        resolveArtifacts(javaLibrary) == ["commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"]
        resolveAdditionalArtifacts(javaLibrary) == ["publishTest-1.9-source.jar"]
    }

    @Issue("GRADLE-3514")
    void "generated ivy descriptor includes dependency exclusions"() {
        given:
        createBuildScripts("""
            $dependencies

            dependencies {
                compile 'org.springframework:spring-core:2.5.6', {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                compile "commons-beanutils:commons-beanutils:1.8.3", {
                    exclude group: 'commons-logging'
                }
                compile "commons-dbcp:commons-dbcp:1.4", {
                    transitive = false
                }
                compile "org.apache.camel:camel-jackson:2.15.3", {
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
        javaLibrary.parsedModuleMetadata.variant('api') {
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
        resolveArtifacts(javaLibrary) == [
            "camel-jackson-2.15.3.jar",
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
        ]
    }

    void "defaultDependencies are included in published ivy descriptor"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

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

            ${mavenCentralRepository()}

            configurations.compile.defaultDependencies { deps ->
                deps.add project.dependencies.create("org.test:default-dependency:1.1")
            }
"""

        when:
        succeeds "publish"

        then:
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
                    dep.version { prefer 'X' }
                }
            }
"""

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublishedAsJavaModule()
        javaLibrary.assertApiDependencies('org.test:dep1:X', 'org.test:dep2:X')
    }

    def "can publish java-library with strict dependencies"() {
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
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("org.springframework:spring-core:2.5.6@compile", "commons-collections:commons-collections:3.2.2@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:3.2.2') {
                noMoreExcludes()
                rejects ']3.2.2,)'
            }
            dependency('org.springframework:spring-core:2.5.6') {
                noMoreExcludes()
                rejects()
            }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) == [
            'commons-collections-3.2.2.jar', 'commons-logging-1.1.1.jar', 'publishTest-1.9.jar', 'spring-core-2.5.6.jar'
        ]
    }

    def "can publish java-library with dependency constraints"() {
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
                    maven(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("org.springframework:spring-core:1.2.9@compile", "org.apache.commons:commons-compress:1.5@runtime")
        // we do not publish constraints to ivy

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
            constraint('org.tukaani:xz:1.6') { rejects(']1.6,)') }

            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary) == [
            'commons-compress-1.5.jar', 'commons-logging-1.2.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.6.jar'
        ]

        when:
        resolveModuleMetadata = false

        then: "constraints are not published to POM files"
        resolveArtifacts(javaLibrary) == [
            'commons-compress-1.5.jar', 'commons-logging-1.0.4.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.2.jar'
        ]
    }

    def "can publish java-library with dependencies without version"() {
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
                    maven(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        // This currently fails, because the ivy.xml does not provide a version. There are several options to fix this.
        // One or more of them will be implemented and then this test needs to be updated.
        // - Do not publish the incomplete ivy.xml (only the module metadata)
        // - Publish constraints to ivy by substituting the empty version with the one from the constraint
        fails "publish"

        then:
        failure.assertHasCause("Could not parse Ivy file ")
        failure.assertHasCause("xml parsing: ivy.xml:13:98: cvc-complex-type.4: Attribute 'rev' must appear on element 'dependency'.")

        /*javaLibrary.assertPublished()
        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:3.2.2@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:1') {
                rejects()
                noMoreExcludes()
            }
            constraint('commons-collections:commons-collections:3.2.2') { rejects() }
            noMoreDependencies()
        }

        and:
        resolveArtifacts(javaLibrary)*/
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
}
