/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TestProjectGeneratorTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "generates project hierarchy with correct depth"() {
        given:
        def config = JavaTestProjectGenerator.LARGE_JAVA_MULTI_PROJECT_HIERARCHY.config
        def generator = new TestProjectGenerator(config)
        def outputDir = temporaryFolder.createDir("output")

        when:
        generator.generate(outputDir)

        then:
        def rootDir = new File(outputDir, config.projectName)
        rootDir.exists()

        // Check project depth (should be 5 levels deep)
        def depthProject = rootDir.listFiles().find { it.name == "project0" }
        def previousProject = ""
        for (int i = 0; i < config.projectDepth; i++) {
            depthProject = new File(depthProject, "sub${i}project0")
            assert depthProject.exists()
        }

        // Verify no deeper levels exist
        !new File(depthProject, "sub${config.projectDepth}project0").exists()
    }

    def "generates correct number of subprojects"() {
        given:
        def config = JavaTestProjectGenerator.LARGE_JAVA_MULTI_PROJECT_HIERARCHY.config
        def generator = new TestProjectGenerator(config)
        def outputDir = temporaryFolder.createDir("output")

        when:
        generator.generate(outputDir)

        then:
        def rootDir = new File(outputDir, config.projectName)
        def subprojectDirs = rootDir.listFiles().findAll { it.isDirectory() && it.name.startsWith("project") }
        subprojectDirs.size() == config.subProjects
    }

    def "generates correct number of source files per project"() {
        given:
        def config = JavaTestProjectGenerator.LARGE_JAVA_MULTI_PROJECT_HIERARCHY.config
        def generator = new TestProjectGenerator(config)
        def outputDir = temporaryFolder.createDir("output")

        when:
        generator.generate(outputDir)

        then:
        def rootDir = new File(outputDir, config.projectName)
        def sampleProject = new File(rootDir, "project5")

        def productionFiles = findSourceFiles(sampleProject, "src/main/${config.language.name}")
        productionFiles.size() == config.sourceFiles

        def testFiles = findSourceFiles(sampleProject, "src/test/${config.language.name}")
        testFiles.size() == config.sourceFiles
    }

    def "generates expected gradle files"() {
        given:
        def config = JavaTestProjectGenerator.LARGE_JAVA_MULTI_PROJECT_HIERARCHY.config
        def generator = new TestProjectGenerator(config)
        def outputDir = temporaryFolder.createDir("output")

        when:
        generator.generate(outputDir)

        then:
        def rootDir = new File(outputDir, config.projectName)
        new File(rootDir, config.dsl.fileNameFor('build')).exists()
        new File(rootDir, config.dsl.fileNameFor('settings')).exists()
        new File(rootDir, "gradle.properties").exists()

        def subproject = new File(rootDir, "project10")
        new File(subproject, config.dsl.fileNameFor('build')).exists()
    }

    private List<File> findSourceFiles(File dir, String path) {
        def sourceDir = new File(dir, path)
        if (!sourceDir.exists()) {
            return []
        }

        def result = []
        sourceDir.eachFileRecurse { file ->
            if (file.isFile() && (file.name.endsWith(".java") || file.name.endsWith(".groovy") || file.name.endsWith(".kt"))) {
                result << file
            }
        }
        return result
    }

    private List<File> findAllProjectDirs(File rootDir) {
        def result = [rootDir]
        rootDir.listFiles().each { file ->
            if (file.isDirectory() && file.name.startsWith("project")) {
                result.add(file)
                // Find nested projects
                for (int i = 1; i <= 5; i++) {
                    def nestedProject = new File(file, "sub${i}project" + file.name.substring(7))
                    if (nestedProject.exists()) {
                        result.add(nestedProject)
                    }
                }
            }
        }
        return result
    }
}
