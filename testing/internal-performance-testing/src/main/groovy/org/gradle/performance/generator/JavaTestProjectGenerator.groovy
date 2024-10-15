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

package org.gradle.performance.generator

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.language.Language

import static org.gradle.test.fixtures.dsl.GradleDsl.DECLARATIVE
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

@CompileStatic
enum JavaTestProjectGenerator {
    HUGE_JAVA_MULTI_PROJECT(new TestProjectGeneratorConfigurationBuilder('hugeJavaMultiProject')
        .withSourceFiles(500)
        .withSubProjects(500)
        .withDaemonMemory('1536m')
        .withCompilerMemory('1g')
        .assembleChangeFile()
        .testChangeFile(450, 2250, 45000).create()
    ),
    LARGE_MONOLITHIC_JAVA_PROJECT(new TestProjectGeneratorConfigurationBuilder("largeMonolithicJavaProject")
        .withSourceFiles(50000)
        .withSubProjects(0)
        .withDaemonMemory('1536m')
        .withCompilerMemory('4g')
        .assembleChangeFile(-1)
        .testChangeFile(-1)
        .create()),
    LARGE_JAVA_MULTI_PROJECT(new TestProjectGeneratorConfigurationBuilder("largeJavaMultiProject")
        .withSourceFiles(100)
        .withSubProjects(500)
        .withDaemonMemory('2g')
        .withCompilerMemory('512m')
        .assembleChangeFile()
        .testChangeFile(450, 2250, 45000).create()),
    LARGE_JAVA_MULTI_PROJECT_HIERARCHY(new TestProjectGeneratorConfigurationBuilder("largeJavaMultiProjectHierarchy")
        .withSourceFiles(100)
        .withSubProjects(250)
        .withProjectDepth(5)
        .withDaemonMemory('2g')
        .withCompilerMemory('512m')
        .assembleChangeFile()
        .testChangeFile(450, 2250, 45000).create()),
    LARGE_MONOLITHIC_GROOVY_PROJECT(new TestProjectGeneratorConfigurationBuilder("largeMonolithicGroovyProject", Language.GROOVY)
        .withSourceFiles(50000)
        .withSubProjects(0)
        .withDaemonMemory('3g')
        .withCompilerMemory('6g')
        .withSystemProperties(['org.gradle.groovy.compilation.avoidance': 'true'])
        .withFeaturePreviews('GROOVY_COMPILATION_AVOIDANCE')
        .assembleChangeFile(-1)
        .testChangeFile(-1).create()),
    LARGE_GROOVY_MULTI_PROJECT(new TestProjectGeneratorConfigurationBuilder("largeGroovyMultiProject", Language.GROOVY)
        .withSourceFiles(100)
        .withSubProjects(500)
        .withDaemonMemory('2g')
        .withCompilerMemory('256m')
        .withSystemProperties(['org.gradle.groovy.compilation.avoidance': 'true'])
        .withFeaturePreviews('GROOVY_COMPILATION_AVOIDANCE')
        .assembleChangeFile()
        .testChangeFile(450, 2250, 45000).create()),
    LARGE_JAVA_MULTI_PROJECT_NO_BUILD_SRC(
        new TestProjectGeneratorConfigurationBuilder("largeJavaMultiProjectNoBuildSrc", "largeJavaMultiProject")
            .withBuildSrc(false)
            .withSourceFiles(100)
            .withSubProjects(500)
            .withDaemonMemory('1536m')
            .withCompilerMemory('256m')
            .assembleChangeFile()
            .testChangeFile(450, 2250, 45000)
            .create()
    ),
    LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL(new TestProjectGeneratorConfigurationBuilder("largeJavaMultiProjectKotlinDsl", "largeJavaMultiProject")
        .withSourceFiles(100)
        .withSubProjects(500)
        .withDaemonMemory('1536m')
        .withCompilerMemory('256m')
        .assembleChangeFile()
        .testChangeFile(450, 2250, 45000)
        .withDsl(KOTLIN)
        .create()),

    LARGE_EMPTY_MULTI_PROJECT_DECLARATIVE_DSL(new TestProjectGeneratorConfigurationBuilder("largeEmptyMultiProjectDeclarativeDsl", "largeEmptyMultiProject")
        .withSubProjects(500)
        .withDsl(DECLARATIVE)
        .withDaemonMemory('512m')
        .withCompilerMemory('1g')
        .create()),

    MEDIUM_MONOLITHIC_JAVA_PROJECT(new TestProjectGeneratorConfigurationBuilder("mediumMonolithicJavaProject")
        .withSourceFiles(10000)
        .withSubProjects(0)
        .withDaemonMemory('512m')
        .withCompilerMemory('1g')
        .assembleChangeFile(-1)
        .create()),
    MEDIUM_JAVA_MULTI_PROJECT(new TestProjectGeneratorConfigurationBuilder("mediumJavaMultiProject")
        .withSourceFiles(100)
        .withSubProjects(100)
        .withDaemonMemory('512m')
        .withCompilerMemory('256m')
        .assembleChangeFile()
        .create()),
    MEDIUM_JAVA_COMPOSITE_BUILD(new TestProjectGeneratorConfigurationBuilder("mediumJavaCompositeBuild", "mediumJavaMultiProject")
        .withSourceFiles(100)
        .withSubProjects(100)
        .withDaemonMemory('768m')
        .withCompilerMemory('256m')
        .assembleChangeFile()
        .composite(false)
        .create()),
    MEDIUM_JAVA_PREDEFINED_COMPOSITE_BUILD(new TestProjectGeneratorConfigurationBuilder("mediumJavaPredefinedCompositeBuild", "mediumJavaMultiProject")
        .withSourceFiles(100)
        .withSubProjects(100)
        .withDaemonMemory('768m')
        .withCompilerMemory('256m')
        .assembleChangeFile()
        .composite(true)
        .create()),
    MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG(new TestProjectGeneratorConfigurationBuilder("mediumJavaMultiProjectWithTestNG")
        .withSourceFiles(100)
        .withSubProjects(100)
        .withDaemonMemory('512m')
        .withCompilerMemory('256m')
        .assembleChangeFile()
        .testChangeFile(50, 250, 5000)
        .withUseTestNG(true)
        .create()),
    SMALL_JAVA_MULTI_PROJECT(new TestProjectGeneratorConfigurationBuilder("smallJavaMultiProject")
        .withSourceFiles(50)
        .withSubProjects(10)
        .withDaemonMemory("256m")
        .withCompilerMemory("64m")
        .assembleChangeFile()
        .create()),
    SMALL_JAVA_MULTI_PROJECT_MANY_EXTERNAL_DEPENDENCIES(new TestProjectGeneratorConfigurationBuilder("smallJavaMultiProjectManyExternalDependencies")
        .withSourceFiles(50)
        .withSubProjects(10)
        .withDaemonMemory('2g')
        .withCompilerMemory('512m')
        .assembleChangeFile()
        .withExternalApiDependencies([
            "spring": "org.springframework.boot:spring-boot:2.7.9",
            "aws": "com.amazonaws:aws-java-sdk-bundle:1.12.680",
            "jooq": "org.jooq:jooq:3.19.6",
            "grpc": "io.grpc:grpc-netty:1.62.2",
            "otel" : "io.opentelemetry:opentelemetry-sdk:1.33.0",
            "jackson" : "com.fasterxml.jackson.core:jackson-databind:2.10.0",
            "junit5" : "org.junit.jupiter:junit-jupiter-engine:5.10.0",
            "kotlin": "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "testcontainers": "org.testcontainers:mysql:1.15.3",
            "vertx": "io.vertx:vertx-web:4.4.2",
            "keycloak": "org.keycloak:keycloak-core:24.0.1"
        ])
        .create()),
    SMALL_JAVA_MULTI_PROJECT_NO_BUILD_SRC(new TestProjectGeneratorConfigurationBuilder('smallJavaMultiProjectNoBuildSrc')
        .withSourceFiles(50)
        .withSubProjects(10)
        .withDaemonMemory("256m")
        .withCompilerMemory("64m")
        .assembleChangeFile()
        .withBuildSrc(false).create())

    private TestProjectGeneratorConfiguration config

    JavaTestProjectGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
    }

    TestProjectGeneratorConfiguration getConfig() {
        return config
    }

    String getProjectName() {
        return config.projectName
    }

    String getDaemonMemory() {
        return config.daemonMemory
    }

    def getParallel() {
        return config.parallel
    }

    def getMaxWorkers() {
        return config.maxWorkers
    }

    @Override
    String toString() {
        return config.projectName
    }
}
