/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.testing

import org.gradle.api.DependencyManager
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.compile.ClasspathConverter
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class Test extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Test)

    static final String SKIP_TEST = 'gradle.test.skip'
    
    Test self

    AntJunit antJunit = new AntJunit()

    DependencyManager dependencyManager = null

    File compiledTestsDir = null

    File testResultsDir = null

    JunitOptions options = new JunitOptions()

    List includes = []

    List excludes = []

    ExistingDirsFilter existingDirsFilter = new ExistingDirsFilter()

    ClasspathConverter classpathConverter = new ClasspathConverter()

    List unmanagedClasspath = []

    boolean stopAtFailuresOrErrors = true

    Test(DefaultProject project, String name) {
        super(project, name)
        actions << this.&executeTests
        skipProperties << SKIP_TEST
        self = this
    }

    protected void executeTests(Task task) {
        if (!self.compiledTestsDir) throw new InvalidUserDataException("The compiledTestDir property is not set, testing can't be triggered!")
        if (!self.testResultsDir) throw new InvalidUserDataException("The testResultsDir property is not set, testing can't be triggered!")

        if (!existingDirsFilter.checkExistenceAndLogExitMessage(self.compiledTestsDir)) {return}

        List classpath = classpathConverter.createFileClasspath(
                project.rootDir, self.unmanagedClasspath as Object[]) + self.dependencyManager.resolveClasspath(name)

        antJunit.execute(self.compiledTestsDir, classpath, self.testResultsDir, includes, excludes, options, project.ant)
        if (stopAtFailuresOrErrors && project.ant.project.getProperty(AntJunit.FAILURES_OR_ERRORS_PROPERTY)) {
            throw new GradleException("There were failing tests!")
        }
    }

    Test include(String[] includes) {
        this.includes += (includes as List)
        this
    }

    Test exclude(String[] excludes) {
        this.excludes += excludes as List
        this
    }

}