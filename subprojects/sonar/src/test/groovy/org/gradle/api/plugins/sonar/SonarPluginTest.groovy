/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar

import org.gradle.api.plugins.sonar.model.SonarRootModel
import org.gradle.api.plugins.sonar.model.SonarProjectModel
import org.gradle.api.plugins.sonar.model.SonarProject
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.ConfigureUtil
import org.gradle.util.HelperUtil
import org.gradle.internal.jvm.Jvm

import spock.lang.Specification
import spock.lang.Issue

class SonarPluginTest extends Specification {
    def "adds model and task to root project"() {
        def project = HelperUtil.createRootProject()

        when:
        project.plugins.apply(SonarPlugin)

        then:
        project.sonar instanceof SonarRootModel
        project.tasks.findByName("sonarAnalyze")
    }

    def "adds model to subprojects"() {
        def project = HelperUtil.createRootProject()
        def child = HelperUtil.createChildProject(project, "child")

        when:
        project.plugins.apply(SonarPlugin)

        then:
        child.sonar instanceof SonarProjectModel
        !child.tasks.findByName("sonarAnalyze")
    }

    def "provides defaults for global configuration"() {
        def project = HelperUtil.createRootProject()

        when:
        project.plugins.apply(SonarPlugin)

        then:
        SonarRootModel sonar = project.sonar

        sonar.server.url == "http://localhost:9000"

        def db = sonar.database
        db.username == "sonar"
        db.password == "sonar"

        sonar.bootstrapDir instanceof File
        sonar.gradleVersion == project.gradle.gradleVersion
    }

    def "provides defaults for project configuration"(Project project) {
        SonarProject sonarProject = project.sonar.project

        expect:
        sonarProject.key == "$project.group%3A$project.name"
        sonarProject.name == project.name
        sonarProject.description == project.description
        sonarProject.version == project.version
        !sonarProject.skip
        sonarProject.baseDir == project.projectDir
        sonarProject.workDir == new File(project.buildDir, "sonar")
        sonarProject.dynamicAnalysis == "reuseReports"

        where:
        project << createMultiProject().allprojects
    }

    def "provides additional defaults for project configuration if java-base plugin is present"() {
        SonarProject sonarProject = project.sonar.project

        expect:
        sonarProject.java.sourceCompatibility == project.sourceCompatibility as String
        sonarProject.java.targetCompatibility == project.targetCompatibility as String

        where:
        project << createMultiProject { plugins.apply(JavaBasePlugin) }.allprojects
    }

    def "provides additional defaults for project configuration if java plugin is present"(Project project) {
        SonarProject sonarProject = project.sonar.project

        project.sourceSets.main.allSource.srcDirs*.mkdirs()
        project.sourceSets.test.allSource.srcDirs*.mkdirs()
        project.sourceSets.main.output.classesDir.mkdirs()
        project.test.testResultsDir.mkdirs()

        expect:
        sonarProject.sourceDirs as Set == project.sourceSets.main.allSource.srcDirs as Set
        sonarProject.testDirs as Set == project.sourceSets.test.allSource.srcDirs as Set
        sonarProject.binaryDirs as Set == [project.sourceSets.main.output.classesDir] as Set
        sonarProject.libraries as Set == [Jvm.current().runtimeJar] as Set

        sonarProject.testReportPath == project.test.testResultsDir
        sonarProject.language == "java"

        where:
        project << createMultiProject { plugins.apply(JavaPlugin) }.allprojects
    }

    @Issue("http://forums.gradle.org/gradle/topics/gradle_multi_project_build_with_sonar_and_cobertura")
    def "'dynamicAnalysis' always defaults to 'reuseReports', even for root project and if Java plugin isn't applied"() {
        def project = createMultiProject()
        def childProject = project.subprojects.iterator().next()

        expect:
        project.sonar.project.dynamicAnalysis == "reuseReports"
        childProject.sonar.project.dynamicAnalysis == "reuseReports"
    }

    private Project createMultiProject(Closure commonConfig = {}) {
        def root = HelperUtil.createRootProject()
        ConfigureUtil.configure(commonConfig, root)
        root.group = "group"

        def child = HelperUtil.createChildProject(root, "child")
        ConfigureUtil.configure(commonConfig, child)
        child.group = "group"

        root.plugins.apply(SonarPlugin)

        root
    }
}
