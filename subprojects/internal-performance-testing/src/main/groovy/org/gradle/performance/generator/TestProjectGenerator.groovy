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
        def rootProjectDir = new File(outputBaseDir, config.projectName)
        rootProjectDir.mkdirs()

        generateProject(rootProjectDir, null)
        for (int i = 0; i < config.subProjects; i++) {
            def subProjectDir = new File(rootProjectDir, "project$i")
            generateProject(subProjectDir, i)
        }
    }

    def generateProject(File projectDir, Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        file projectDir, "build.gradle", fileContentGenerator.generateBuildGradle(isRoot)
        file projectDir, "settings.gradle", fileContentGenerator.generateSettingsGradle(isRoot)
        file projectDir, "gradle.properties", fileContentGenerator.generateGradleProperties(isRoot)

        if (!isRoot || config.subProjects == 0) {
            def sourceFileRangeStart = isRoot ? 0 : subProjectNumber * config.sourceFiles
            def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
            (sourceFileRangeStart..sourceFileRangeEnd).each {
                def packageName = fileContentGenerator.packageName(it, subProjectNumber, '/')
                file projectDir, "src/main/java/${packageName}/Production${it}.java", fileContentGenerator.generateProductionClassFile(subProjectNumber, it)
                file projectDir, "src/test/java/${packageName}/Test${it}.java", fileContentGenerator.generateTestClassFile(subProjectNumber, it)
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
}
