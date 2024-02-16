/*
 * Copyright 2024 the original author or authors.
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

import static org.gradle.test.fixtures.dsl.GradleDsl.DECLARATIVE

@CompileStatic
class DeclarativeDslTestProjectGenerator extends AbstractTestProjectGenerator {

    final TestProjectGeneratorConfiguration config

    DeclarativeDslTestProjectGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config

        if (config.dsl != DECLARATIVE) {
            throw new IllegalArgumentException("Template ${config.templateName} only supports the ${DECLARATIVE.name()} DSL")
        }

        if (config.compositeBuild) {
            throw new IllegalArgumentException("Template ${config.templateName} doesn't support composite builds")
        }
    }

    def generate(File outputBaseDir) {
        generateProjects(outputBaseDir)
    }

    def generateProjects(File outputBaseDir) {
        def rootProjectDir = new File(outputBaseDir, config.projectName)
        rootProjectDir.mkdirs()
        generateProject(rootProjectDir, null)
        for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
            def subProjectDir = new File(rootProjectDir, "project$subProjectNumber")
            generateProject(subProjectDir, subProjectNumber)
        }
        for (char libId = 'A'; libId <= ('C' as char); libId++) {
            def libProjectDir = new File(rootProjectDir, "lib$libId")
            generateLibProject(libProjectDir)
        }
    }

    def generateProject(File projectDir, Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        file projectDir, config.dsl.fileNameFor('build'), generateBuildGradle(subProjectNumber)
        file projectDir, config.dsl.fileNameFor('settings'), generateSettingsGradle(isRoot)
    }

    def generateLibProject(File projectDir) {
        file projectDir, config.dsl.fileNameFor('build'), """
            plugins {
                id("java-library")
            }
        """.stripIndent()
    }

    String generateSettingsGradle(boolean isRoot) {
        if (!isRoot) {
            return null
        }

        String includedProjects = """
            rootProject.name = "root-project"

            include(":libA")
            include(":libB")
            include(":libC")

        """.stripIndent()

        if (config.subProjects != 0) {
            includedProjects += """${(0..config.subProjects - 1).collect { "include(\"project$it\")" }.join("\n")}"""
        }

        return includedProjects
    }

    String generateBuildGradle(Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        if (isRoot) {
            return """
                plugins {
                    id("base")
                }
            """.stripIndent()
        }

        return """
            plugins {
                id("application")
            }

            dependencies {
                implementation(project(":libA"))
                implementation(project(":libB"))
                implementation(project(":libC"))
            }

            // comment to make each file different $subProjectNumber
        """.stripIndent()
    }

    static void main(String[] args) {
        def projectName = args[0]
        def outputDir = new File(args[1])

        JavaTestProjectGenerator project = JavaTestProjectGenerator.values().find {it.projectName == projectName }
        if (project == null) {
            throw new IllegalArgumentException("Project not defined: $projectName")
        }
        def projectDir = new File(outputDir, projectName)
        new DeclarativeDslTestProjectGenerator(project.config).generate(outputDir)

        println "Generated: $projectDir"
    }

}
