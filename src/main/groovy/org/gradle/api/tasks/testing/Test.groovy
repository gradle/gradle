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

    /**
     * The directory with the compiled test classes
     */
    File testClassesDir = null

    /**
     * The directory where the test results are put. Right now only the xml format of the test results is supported.
     */
    File testResultsDir = null

    /**
     * This task delegates to Ants Junit task for the execution of the tests. This options contain most of the options
     * of Ants Junit task.
     */
    JunitOptions options = new JunitOptions()

    /**
     * Include pattern for the filess in the test classes directory (e.g. '**&#2F;*Test.class')).
     */
    List includes = []

    /**
     * Exclude pattern for the filess in the test classes directory (e.g. '**&#2F;Abstract*.class').
     */
    List excludes = []

    /**
     * If true the build stops with an excepton in case a test fails or throws an uncatched exception. The build
     * stops in this case after ALL tests have been executed.
     */
    boolean stopAtFailuresOrErrors = true

    /**
     * This property is used internally by Gradle. It is usually not used by build scripts.
     * A list of files added to the compile classpath. The files should point to jars or directories containing
     * class files. The files added here are not shared in a multi-project build and are not mentioned in
     * a dependency descriptor if you upload your library to a repository.
     */
    List unmanagedClasspath = []

    AntJunit antJunit = new AntJunit()

    protected Test self

    protected DependencyManager dependencyManager = null

    protected ExistingDirsFilter existingDirsFilter = new ExistingDirsFilter()

    protected ClasspathConverter classpathConverter = new ClasspathConverter()

    Test(DefaultProject project, String name) {
        super(project, name)
        actions << this.&executeTests
        self = this
    }

    protected void executeTests(Task task) {
        if (!self.testClassesDir) throw new InvalidUserDataException("The testClassesDir property is not set, testing can't be triggered!")
        if (!self.testResultsDir) throw new InvalidUserDataException("The testResultsDir property is not set, testing can't be triggered!")

        existingDirsFilter.checkExistenceAndThrowStopActionIfNot(self.testClassesDir)

        List classpath = classpathConverter.createFileClasspath(
                project.rootDir, [self.testClassesDir] + self.unmanagedClasspath as Object[]) + self.dependencyManager.resolveClasspath(name)

        antJunit.execute(self.testClassesDir, classpath, self.testResultsDir, includes, excludes, options, project.ant)
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

    Test unmanagedClasspath(Object[] elements) {
        self.unmanagedClasspath.addAll((elements as List).flatten())
        this
    }

}