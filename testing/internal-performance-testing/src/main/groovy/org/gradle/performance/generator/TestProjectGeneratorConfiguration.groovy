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
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.language.Language

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

@CompileStatic
class TestProjectGeneratorConfiguration {
    String projectName
    String templateName

    Language language
    GradleDsl dsl

    String[] plugins
    String[] repositories
    Map<String, String> externalApiDependencies
    Map<String, String> externalImplementationDependencies

    boolean buildSrc

    int subProjects
    int sourceFiles
    int minLinesOfCodePerSourceFile
    int projectDepth = 0
    CompositeConfiguration compositeBuild

    String daemonMemory
    String compilerMemory
    String testRunnerMemory
    Map<String, String> systemProperties
    String[] featurePreviews
    boolean parallel
    int maxWorkers
    int maxParallelForks
    int testForkEvery
    boolean useTestNG
    Map<String, String> fileToChangeByScenario
}

@Builder(prefix = "with",
    builderStrategy = ExternalStrategy,
    forClass = TestProjectGeneratorConfiguration,
    excludes = [
        'plugins',
        'repositories',
        'externalImplementationDependencies',
        'minLinesOfCodePerSourceFile',
        'testRunnerMemory',
        'parallel',
        'maxWorkers',
        'testForkEvery',
        'maxParallelForks'
    ])
class TestProjectGeneratorConfigurationBuilder {
    TestProjectGeneratorConfigurationBuilder(String projectName, Language language = Language.JAVA) {
        this(projectName, projectName, language)
    }

    TestProjectGeneratorConfigurationBuilder(String projectName, String templateName, Language language = Language.JAVA) {
        this.projectName = projectName
        this.templateName = templateName
        this.language = language
        this.dsl = GradleDsl.GROOVY
        this.buildSrc = true
        this.fileToChangeByScenario = [:]
        this.systemProperties = [:]
        this.featurePreviews = [] as String[]
        this.externalApiDependencies = [
            commonsLang: 'commons-lang:commons-lang:2.5',
            commonsHttpClient: 'commons-httpclient:commons-httpclient:3.0',
            commonsCodec: 'commons-codec:commons-codec:1.2',
            jclOverSlf4j: 'org.slf4j:jcl-over-slf4j:1.7.10',
        ]
    }

    TestProjectGeneratorConfigurationBuilder assembleChangeFile(int project = 0, int pkg = 0, int file = 0) {
        this.fileToChangeByScenario['assemble'] = productionFile(project, pkg, file)
        return this
    }

    TestProjectGeneratorConfigurationBuilder testChangeFile(int project = 0, int pkg = 0, int file = 0) {
        this.fileToChangeByScenario['test'] = productionFile(project, pkg, file)
        return this
    }


    private String productionFile(int project = 0, int pkg = 0, int file = 0) {
        if (project >= 0) {
            "project${project}/src/main/${this.language.name}/org/gradle/test/performance/${this.templateName.toLowerCase()}/project${project}/p${pkg}/Production${file}.${this.language.name}"
        } else {
            "src/main/${this.language.name}/org/gradle/test/performance/${this.templateName.toLowerCase()}/p${pkg}/Production${file}.${this.language.name}"
        }
    }

    TestProjectGeneratorConfigurationBuilder composite(boolean predifined) {
        this.compositeBuild = CompositeConfiguration.composite(predifined)
        return this
    }

    TestProjectGeneratorConfiguration create() {
        TestProjectGeneratorConfiguration config = new TestProjectGeneratorConfiguration()
        config.projectName = this.projectName
        config.templateName = this.templateName
        config.language = this.language
        config.dsl = this.dsl
        config.buildSrc = this.buildSrc

        config.plugins = this.language == Language.GROOVY ? ['groovy', 'java', 'eclipse', 'idea'] : ['java', 'eclipse', 'idea']
        config.repositories = [mavenCentralRepositoryDefinition(this.dsl)]
        config.externalApiDependencies = this.externalApiDependencies
        config.externalImplementationDependencies = [
            reflectasm: 'com.googlecode:reflectasm:1.01',
        ]

        config.subProjects = this.subProjects
        config.projectDepth = this.projectDepth
        config.sourceFiles = this.sourceFiles
        config.minLinesOfCodePerSourceFile = 100
        config.compositeBuild = this.compositeBuild

        config.daemonMemory = this.daemonMemory
        config.compilerMemory = this.compilerMemory
        config.testRunnerMemory = '256m'
        config.parallel = this.subProjects > 0
        config.systemProperties = this.systemProperties
        config.featurePreviews = this.featurePreviews

        config.maxWorkers = 4
        config.maxParallelForks = this.subProjects > 0 ? 1 : 4
        config.testForkEvery = 1000
        config.useTestNG = this.useTestNG
        config.fileToChangeByScenario = this.fileToChangeByScenario
        return config
    }
}
