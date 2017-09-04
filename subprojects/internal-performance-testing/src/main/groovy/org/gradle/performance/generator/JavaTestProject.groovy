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

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import static org.gradle.performance.generator.CompositeConfiguration.composite

@CompileStatic
enum JavaTestProject {

    LARGE_MONOLITHIC_JAVA_PROJECT("largeMonolithicJavaProject", 50000, 0, '4g', false, [assemble: productionFile('largeMonolithicJavaProject', -1), test: productionFile('largeMonolithicJavaProject', -1)]),
    LARGE_JAVA_MULTI_PROJECT("largeJavaMultiProject", 100, 500, '256m', false, [assemble: productionFile('largeJavaMultiProject'), test: productionFile('largeJavaMultiProject', 450, 2250, 45000)]),

    MEDIUM_MONOLITHIC_JAVA_PROJECT("mediumMonolithicJavaProject", 10000, 0, '4g', false, [assemble: productionFile('mediumMonolithicJavaProject', -1)]),
    MEDIUM_JAVA_MULTI_PROJECT("mediumJavaMultiProject", 100, 100, '256m', false, [assemble: productionFile('mediumJavaMultiProject')]),
    MEDIUM_JAVA_COMPOSITE_BUILD("mediumJavaCompositeBuild", composite(false),100, 100, '256m', false, [assemble: productionFile('mediumJavaMultiProject')]),
    MEDIUM_JAVA_PREDEFINED_COMPOSITE_BUILD("mediumJavaPredefinedCompositeBuild", composite(true),100, 100, '256m', false, [assemble: productionFile('mediumJavaMultiProject')]),

    MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG("mediumJavaMultiProjectWithTestNG", 100, 100, '256m', true, [assemble: productionFile('mediumJavaMultiProjectWithTestNG'), test: productionFile('mediumJavaMultiProjectWithTestNG', 50, 250, 5000)]),

    SMALL_JAVA_MULTI_PROJECT("smallJavaMultiProject", 50, 10, '256m', false, [assemble: productionFile('smallJavaMultiProject')]),

    private TestProjectGeneratorConfiguration config

    JavaTestProject(String projectName, CompositeConfiguration compositeConfiguration=null, int sourceFiles, int subProjects, String compilerMemory, boolean useTestNG, Map<String, String> filesToUpdate) {
        this.config = new TestProjectGeneratorConfiguration()
        config.projectName = projectName

        config.plugins = ['java', 'eclipse', 'idea']
        config.repositories = [mavenCentralRepositoryDefinition()]
        config.externalApiDependencies = ['commons-lang:commons-lang:2.5', 'commons-httpclient:commons-httpclient:3.0',
                                          'commons-codec:commons-codec:1.2', 'org.slf4j:jcl-over-slf4j:1.7.10']
        config.externalImplementationDependencies = ['com.googlecode:reflectasm:1.01']

        config.subProjects = subProjects
        config.sourceFiles = sourceFiles
        config.minLinesOfCodePerSourceFile = 100
        config.daemonMemory = '2g'
        config.compilerMemory = compilerMemory
        config.testRunnerMemory = '256m'
        config.parallel = subProjects > 0
        config.maxWorkers = 4
        config.maxParallelForks = subProjects > 0 ? 1 : 4
        config.testForkEvery = 10000
        config.useTestNG = useTestNG
        config.fileToChangeByScenario = filesToUpdate
        config.compositeBuild = compositeConfiguration
    }

    private static String productionFile(String template, int project = 0, int pkg = 0, int file = 0) {
        if (project>=0) {
            "project${project}/src/main/java/org/gradle/test/performance/${template.toLowerCase()}/project${project}/p${pkg}/Production${file}.java"
        } else {
            "src/main/java/org/gradle/test/performance/${template.toLowerCase()}/p${pkg}/Production${file}.java"
        }
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
