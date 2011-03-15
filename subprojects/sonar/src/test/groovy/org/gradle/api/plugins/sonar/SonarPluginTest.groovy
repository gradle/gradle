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

import org.gradle.util.HelperUtil
import org.gradle.api.plugins.JavaPlugin

import spock.lang.Specification

class SonarPluginTest extends Specification {
    def "only adds sonar task if Java plugin is present"() {
        def project = HelperUtil.createRootProject()

        when:
        project.plugins.apply(SonarPlugin)

        then:
        !project.tasks.findByName("sonar")

        when:
        project.plugins.apply(JavaPlugin)

        then:
        project.tasks.findByName("sonar")
    }

    def "provides default configuration for sonar task"() {
        def project = HelperUtil.createRootProject()
        project.plugins.apply(JavaPlugin)
        project.sourceSets.main.java.srcDir("src/main/other")
        project.sourceSets.test.java.srcDir("src/test/other")
        project.group = "testGroup"
        project.description = "testDescription"
        project.sourceCompatibility = "1.6"
        project.targetCompatibility = "1.5"

        when:
        project.plugins.apply(SonarPlugin)

        then:
        def task = (Sonar) project.tasks.getByName("sonar")
        task.serverUrl == "http://localhost:9000"
        task.bootstrapDir.isDirectory()
        task.projectDir == project.projectDir
        task.buildDir == project.buildDir
        task.projectMainSourceDirs == [project.file("src/main/java"),
                project.file("src/main/resources"), project.file("src/main/other")] as Set
        task.projectTestSourceDirs == [project.file("src/test/java"),
                project.file("src/test/resources"), project.file("src/test/other")] as Set
        task.projectClassesDirs == [project.file("build/classes/main")] as Set
        task.projectDependencies.isEmpty() // because our project doesn't have any dependencies defined
        task.projectKey == "testGroup:test"
        task.projectName == "test"
        task.projectDescription == "testDescription"
        task.globalProperties.isEmpty()
        task.projectProperties.size() == 4
        task.projectProperties["sonar.java.source"] == "1.6"
        task.projectProperties["sonar.java.target"] == "1.5"
        task.projectProperties["sonar.dynamicAnalysis"] == "reuseReports"
        task.projectProperties["sonar.surefire.reportsPath"] == project.file("build/test-results") as String
    }
}
