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

import org.gradle.test.fixtures.ivy.IvyJavaModule
import spock.lang.Issue
import spock.lang.Unroll

class IvyPublishJavaIntegTest extends AbstractIvyPublishIntegTest {
    IvyJavaModule javaLibrary = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))

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
            configurations["default"].extend == ["runtime", "compile"] as Set
            configurations["runtime"].extend == null

            expectArtifact("publishTest").hasAttributes("jar", "jar", ["compile"])
        }
        javaLibrary.assertApiDependencies('commons-collections:commons-collections:3.2.2')
        javaLibrary.assertRuntimeDependencies('commons-io:commons-io:1.4')

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"
        }
    }

    void "can publish java-library-platform to ivy repository"() {
        requiresExternalDependencies = true

        given:
        createBuildScripts("""
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.javaLibraryPlatform
                    }
                }
            }

            $dependencies            
""")

        when:
        run "publish"

        then:
        def backingModule = javaLibrary.backingModule

        backingModule.assertPublished()
        backingModule.assertArtifactsPublished(backingModule.ivyFile.name, backingModule.moduleMetadataFile.name)

        // No files are published for either variant
        with(javaLibrary.parsedModuleMetadata) {
            variants*.name as Set == ['api', 'runtime'] as Set
            variant('api').files.empty
            variant('runtime').files.empty
        }

        with(javaLibrary.parsedIvy) {
            configurations.keySet() == ["default", "compile", "runtime"] as Set
            configurations["default"].extend == ["runtime", "compile"] as Set
            configurations["runtime"].extend == null

            artifacts.empty
        }
        javaLibrary.assertApiDependencies('commons-collections:commons-collections:3.2.2')
        javaLibrary.assertRuntimeDependencies('commons-io:commons-io:1.4')

        and:
        resolveArtifacts(javaLibrary) {
            expectFiles "commons-collections-3.2.2.jar", "commons-io-1.4.jar"
        }
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

    void "ignores extra artifacts added to configurations"() {
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
        requiresExternalDependencies = true
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
        requiresExternalDependencies = true

        given:
        createBuildScripts("""
            configurations.apiElements {
                exclude group: "foo", module: "bar"
            }

            configurations.runtimeElements {
                exclude group: "baz", module: "qux"
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
        javaLibrary.parsedIvy.exclusions.collect { it.org + ":" + it.module + "@" + it.conf} == ["foo:bar@compile", "baz:qux@runtime"]

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('commons-collections:commons-collections:3.2.2') {
                hasExclude('foo', 'bar')
                noMoreExcludes()
            }
        }
        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-io:commons-io:1.4') {
                hasExclude('baz', 'qux')
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
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("org.springframework:spring-core:2.5.6@compile", "commons-collections:commons-collections:3.2.2@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            dependency('org.springframework:spring-core:') {
                noMoreExcludes()
                prefers('2.5.6')
                strictly(null)
                rejects()
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
            dependency('commons-collections:commons-collections:') {
                // TODO:DAZ Validate the 'required' version
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
                expectFiles 'commons-compress-1.5.jar', 'commons-logging-1.0.4.jar', 'publishTest-1.9.jar', 'spring-core-1.2.9.jar', 'xz-1.2.jar'
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
                    maven(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublished()
        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
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
                    assertHasDescription 'Could not resolve all files for configuration'
                    assertHasCause 'Could not find commons-collections:commons-collections:.'
                }
            }
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
        javaLibrary.assertPublished()

        javaLibrary.parsedIvy.configurations.keySet() == ["compile", "runtime", "default"] as Set
        javaLibrary.parsedIvy.assertDependsOn("commons-collections:commons-collections:[3.2, 4)@runtime")

        and:
        javaLibrary.parsedModuleMetadata.variant('api') {
            constraint('commons-logging:commons-logging:') {
                rejects '+'
            }
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant('runtime') {
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
        requiresExternalDependencies = true
        given:
        createBuildScripts("""

            configurations.api.outgoing.capability 'org:foo:1.0'
            configurations.implementation.outgoing.capability 'org:bar:1.0'

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
