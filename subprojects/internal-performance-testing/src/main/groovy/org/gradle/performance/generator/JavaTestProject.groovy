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

enum JavaTestProject {

    LARGE_MONOLITHIC_JAVA_PROJECT("largeMonolithicJavaProject", 50000, 0, '4g'),
    LARGE_JAVA_MULTI_PROJECT("largeJavaMultiProject", 100, 500, '256m'),

    MEDIUM_MONOLITHIC_JAVA_PROJECT("mediumMonolithicJavaProject", 10000, 0, '4g'),
    MEDIUM_JAVA_MULTI_PROJECT("mediumJavaMultiProject", 100, 100, '256m'),

    MEDIUM_JAVA_MULTI_PROJECT_WITH_TEST_NG("mediumJavaMultiProjectWithTestNG", 100, 100, '256m', true)

    private TestProjectGeneratorConfiguration config

    JavaTestProject(String projectName, int sourceFiles, int subProjects, String compilerMemory, boolean useTestNG = false) {
        this.config = new TestProjectGeneratorConfiguration()
        config.projectName = projectName

        config.plugins = ['java', 'eclipse', 'idea']
        config.repositories = ['mavenCentral()']
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
