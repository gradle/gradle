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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Issue

class MavenMultiProjectPublishIntegrationTest extends AbstractIntegrationSpec {
    def mavenModule = mavenRepo.module("org.gradle.test", "project1", "1.9")

    def setup() {
        // the OLD publish plugins work with the OLD deprecated Java plugin configuration (compile/runtime)
        executer.noDeprecationChecks()
        using m2 //uploadArchives leaks into local ~/.m2
    }

    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM if publication coordinates are unchanged"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn("org.gradle.test:project2:1.9")
    }

    @Issue("GRADLE-443")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM if archivesBaseName is changed"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    archivesBaseName = "changed"
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn("org.gradle.test:changed:1.9")
    }

    @Issue("GRADLE-443")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM if mavenDeployer.pom.artifactId is changed"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    uploadArchives {
        repositories.mavenDeployer {
            pom.artifactId = "changed"
        }
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn("org.gradle.test:changed:1.9")
    }

    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM if second artifact is published which differs in classifier"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    task jar2(type: Jar) {
        classifier = "other"
    }

    artifacts {
        archives jar2
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn("org.gradle.test:project2:1.9")
    }


    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM when dependency configuration has no artifacts"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(path: ":project2")
        compile project(path: ":project2", configuration: "otherStuff")
    }
}

project(":project2") {
    configurations {
        otherStuff
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn(
            "org.gradle.test:project2:1.9"
        )
    }

    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM when dependency configuration has multiple classified artifacts"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
        compile project(path: ":project2", configuration: "otherStuff")
        testCompile project(path: ":project2", configuration: "moreStuff")
    }
}

project(":project2") {
    configurations {
        otherStuff
        moreStuff
    }

    task otherJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'otherStuff'
    }

    task otherJarDifferentClassifier(type:Jar) {
        from sourceSets.test.output
        classifier = 'otherStuffDifferentClassifier'
    }

    task moreJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'moreStuff'
    }

    artifacts {
        otherStuff      otherJar
        archives        otherJar
        otherStuff      otherJarDifferentClassifier
        archives        otherJarDifferentClassifier
        moreStuff       moreJar
        archives        moreJar
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn(
            "org.gradle.test:project2:1.9",
            "org.gradle.test:project2:1.9:otherStuff",
            "org.gradle.test:project2:1.9:otherStuffDifferentClassifier")
        pom.scopes.test.assertDependsOn(
            "org.gradle.test:project2:1.9:moreStuff")
    }

    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM when default configuration has classifier"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    jar {
        classifier = 'theDefaultJar'
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn(
            "org.gradle.test:project2:1.9:theDefaultJar")
    }

    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM if configuration has classifier and modified id"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(path: ":project2", configuration: "otherStuff")
    }
}

project(":project2") {
    uploadArchives {
        repositories.mavenDeployer {
            pom.artifactId = "changed"
        }
    }

    configurations {
        otherStuff
    }

    task otherJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'otherStuff'
    }

    artifacts {
        otherStuff  otherJar
        archives    otherJar
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn(
            "org.gradle.test:changed:1.9:otherStuff")
    }

    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM when configuration is extended by other configurations"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(path: ":project2", configuration: "baseConfig")
    }
}

project(":project2") {
    configurations {
        baseConfig
        extendedConfig {
            extendsFrom baseConfig
        }
    }

    task baseConfigJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'baseConfig'
    }

    task extendedConfigJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'extendedConfig'
    }

    artifacts {
        baseConfig      baseConfigJar
        archives        baseConfigJar
        extendedConfig  extendedConfigJar
        archives        extendedConfigJar
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn(
            "org.gradle.test:project2:1.9:baseConfig")
    }

    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "dependency on testRuntime, includes classifier on jar and de-duplicates dependencies"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
        testCompile project(path: ":project2", configuration: "testRuntime")
    }
}

project(":project2") {
    task testJar(type:Jar) {
        from sourceSets.test.output
        classifier = "tests"
    }

    artifacts {
        testRuntime  testJar
    }
}
        """)

        when:
        run(":project1:uploadArchives")

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn("org.gradle.test:project2:1.9")
        pom.scopes.test.assertDependsOn("org.gradle.test:project2:1.9:tests")
    }

    @Issue("GRADLE-3030")
    @ToBeFixedForInstantExecution
    def "project dependency correctly reflected in POM when configuration is extending other configurations"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(path: ":project2", configuration: "extendedConfig")
    }
}

project(":project2") {
    configurations {
        baseConfig
        extendedConfig {
            extendsFrom baseConfig
        }
    }

    task baseConfigJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'baseConfig'
    }

    task extendedConfigJar(type:Jar) {
        from sourceSets.test.output
        classifier = 'extendedConfig'
    }

    artifacts {
        baseConfig      baseConfigJar
        archives        baseConfigJar
        extendedConfig  extendedConfigJar
        archives        extendedConfigJar
    }
}
        """)

        when:
        run ":project1:uploadArchives"

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.compile.assertDependsOn(
            "org.gradle.test:project2:1.9:baseConfig",
            "org.gradle.test:project2:1.9:extendedConfig")
    }

    @ToBeFixedForInstantExecution
    def "dependencies are de-duplicated according to configuration/scope mapping priority"() {
        given:
        settingsFile << "include 'project3'"
        createBuildScripts """
            project(':project1') {
                dependencies {
                    runtime     'commons-collections:commons-collections:3.2.2'
                    testRuntime 'commons-collections:commons-collections:3.2.2'
                    compile     project(':project2')
                    runtime     project(':project2')
                    testCompile project(':project2')
                    testRuntime project(':project2')
                    testCompile project(':project3')
                    testRuntime project(':project3')
                }
            }
        """.stripIndent()

        when:
        run ':project1:uploadArchives'

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.provided == null
        pom.scopes.compile.assertDependsOn 'org.gradle.test:project2:1.9'
        pom.scopes.runtime.assertDependsOn 'commons-collections:commons-collections:3.2.2'
        pom.scopes.test.assertDependsOn 'org.gradle.test:project3:1.9'
    }

    @ToBeFixedForInstantExecution
    def "dependencies are de-duplicated using exclusions from the elected dependency"() {
        given:
        createBuildScripts """
            project(':project1') {
                dependencies {
                    compile(project(':project2')) {
                        exclude group: 'org.slf4j', module: 'slf4j-api'
                    }
                    compile('ch.qos.logback:logback-classic:1.1.7') {
                        exclude group: 'org.slf4j', module: 'slf4j-api'
                    }
                    testCompile(project(':project2')) {
                        exclude group: 'ch.qos.logback', module: 'logback-core'
                    }
                    testRuntime('ch.qos.logback:logback-classic:1.1.7') {
                        exclude group: 'ch.qos.logback', module: 'logback-core'
                    }
                }
            }
        """.stripIndent()

        when:
        run ':project1:uploadArchives'

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.provided == null
        pom.scopes.compile.assertDependsOn 'org.gradle.test:project2:1.9', 'ch.qos.logback:logback-classic:1.1.7'
        def projectDependencyExclusions = pom.scopes.compile.expectDependency('org.gradle.test:project2:1.9').exclusions;
        projectDependencyExclusions.size() == 1
        projectDependencyExclusions[0].groupId == 'org.slf4j'
        projectDependencyExclusions[0].artifactId == 'slf4j-api'
        def externalDependencyExclusions = pom.scopes.compile.expectDependency('ch.qos.logback:logback-classic:1.1.7').exclusions;
        externalDependencyExclusions.size() == 1
        externalDependencyExclusions[0].groupId == 'org.slf4j'
        externalDependencyExclusions[0].artifactId == 'slf4j-api'
        pom.scopes.runtime == null
        pom.scopes.test == null
    }

    @ToBeFixedForInstantExecution
    def "dependencies are de-duplicated using the higher version on the higher scope"() {
        given:
        createBuildScripts """
            project(':project1') {
                dependencies {
                    compile('ch.qos.logback:logback-classic:1.1.5') {
                        exclude group: 'org.slf4j', module: 'slf4j-api'
                    }
                    testCompile 'ch.qos.logback:logback-classic:1.1.6'
                    testRuntime('ch.qos.logback:logback-classic:1.1.7') {
                        exclude group: 'ch.qos.logback', module: 'logback-core'
                    }
                }
            }
        """.stripIndent()

        when:
        run ':project1:uploadArchives'

        then:
        def pom = mavenModule.parsedPom
        pom.scopes.provided == null
        pom.scopes.compile.assertDependsOn 'ch.qos.logback:logback-classic:1.1.7'
        def exclusions = pom.scopes.compile.expectDependency('ch.qos.logback:logback-classic:1.1.7').exclusions;
        exclusions.size() == 1
        exclusions[0].groupId == 'org.slf4j'
        exclusions[0].artifactId == 'slf4j-api'
        pom.scopes.runtime == null
        pom.scopes.test == null
    }

    private void createBuildScripts(String append = "") {
        settingsFile << """
include "project1", "project2"
        """

        buildFile << """
allprojects {
    group = "org.gradle.test"
    version = 1.9
}

subprojects {
    apply plugin: "java"
    apply plugin: "maven"

    uploadArchives {
        repositories {
            mavenDeployer {
                repository(url: "file:///\$rootProject.projectDir/maven-repo")
            }
        }
    }
}

$append
        """
    }
}
