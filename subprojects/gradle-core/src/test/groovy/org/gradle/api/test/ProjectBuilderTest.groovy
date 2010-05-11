/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.test

import spock.lang.Specification
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ProjectBuilderTest extends Specification {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    def canCreateARootProjectWithAGivenProjectDir() {
        when:
        def project = ProjectBuilder.withProjectDir(temporaryFolder.dir).create()

        then:
        project instanceof DefaultProject
        project.name == 'test'
        project.path == ':'
        project.projectDir == temporaryFolder.dir
        project.gradle != null
        project.gradle.gradleHomeDir == temporaryFolder.file('gradleHome')
        project.gradle.gradleUserHomeDir == temporaryFolder.file('userHome')
    }

    def canApplyACustomPlugin() {
        when:
        def project = ProjectBuilder.withProjectDir(temporaryFolder.dir).create()
        project.apply plugin: CustomPlugin

        then:
        project.tasks.hello instanceof DefaultTask
    }

    def canCreateAndExecuteACustomTask() {
        when:
        def project = ProjectBuilder.withProjectDir(temporaryFolder.dir).create()
        def task = project.task('custom', type: CustomTask)
        task.doStuff()

        then:
        task.property == 'some value'
    }

}

public class CustomPlugin implements Plugin<Project> {
    void apply(Project target) {
        target.task('hello');
    }
}

public class CustomTask extends DefaultTask {
    def String property

    @TaskAction
    def doStuff() {
        property = 'some value'
    }
}
