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

class TestProjectGenerator {

    TestProjectGeneratorConfiguration config
    FileContentGenerator fileContentGenerator

    TestProjectGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
        this.fileContentGenerator = new FileContentGenerator(config)
    }

    def generate(File outputBaseDir) {
        def dependencyTree = new DependencyTree()

        populateDependencyTree(dependencyTree)

        generateProjects(outputBaseDir, dependencyTree)
    }

    def populateDependencyTree(DependencyTree dependencyTree) {
        if (config.subProjects == 0) {
            dependencyTree.calculateClassDependencies(0, config.sourceFiles - 1)
        } else {
            for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
                def sourceFileRangeStart = subProjectNumber * config.sourceFiles
                def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
                dependencyTree.calculateClassDependencies(sourceFileRangeStart, sourceFileRangeEnd)
            }
        }
        dependencyTree.calculateProjectDependencies()
    }

    def generateProjects(File outputBaseDir, DependencyTree dependencyTree) {
        def rootProjectDir = new File(outputBaseDir, config.projectName)
        rootProjectDir.mkdirs()
        generateProject(rootProjectDir, dependencyTree, null)
        for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
            def subProjectDir = new File(rootProjectDir, "project$subProjectNumber")
            generateProject(subProjectDir, dependencyTree, subProjectNumber)
        }
    }

    def generateProject(File projectDir, DependencyTree dependencyTree, Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        file projectDir, "build.gradle", fileContentGenerator.generateBuildGradle(subProjectNumber, dependencyTree)
        file projectDir, "settings.gradle", fileContentGenerator.generateSettingsGradle(isRoot)
        file projectDir, "gradle.properties", fileContentGenerator.generateGradleProperties(isRoot)
        file projectDir, "pom.xml", fileContentGenerator.generatePomXML(subProjectNumber, dependencyTree)
        file projectDir, "performance.scenarios", fileContentGenerator.generatePerformanceScenarios(isRoot)

        if (!isRoot || config.subProjects == 0) {
            def sourceFileRangeStart = isRoot ? 0 : subProjectNumber * config.sourceFiles
            def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
            println "Generating Project: $projectDir"
            (sourceFileRangeStart..sourceFileRangeEnd).each {
                def packageName = fileContentGenerator.packageName(it, subProjectNumber, '/')
                file projectDir, "src/main/java/${packageName}/Production${it}.java", fileContentGenerator.generateProductionClassFile(subProjectNumber, it, dependencyTree)
                file projectDir, "src/test/java/${packageName}/Test${it}.java", fileContentGenerator.generateTestClassFile(subProjectNumber, it, dependencyTree)
            }
        }
    }

    void file(File dir, String name, String content) {
        if (content == null) {
            return
        }
        def file = new File(dir, name)
        file.parentFile.mkdirs()
        file.text = content.stripIndent().trim()
    }

    static void main(String[] args) {
        def projectName = args[0]
        def outputDir = new File(args[1])

        JavaTestProject project = JavaTestProject.values().find { it.projectName == projectName }
        if (project == null) {
            throw new IllegalArgumentException("Project not defined: $projectName")
        }
        def projectDir = new File(outputDir, projectName)
        new TestProjectGenerator(project.config).generate(outputDir)

        println "Generated: $projectDir"
    }
}
