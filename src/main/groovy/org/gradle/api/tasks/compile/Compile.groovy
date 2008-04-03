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

package org.gradle.api.tasks.compile

import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
// todo: rename targetDir to destinationDir
class Compile extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Compile)

    List sourceDirs = []

    File targetDir

    String sourceCompatibility

    String targetCompatibility

    List unmanagedClasspath = []

    ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    CompileOptions options = new CompileOptions()

    AbstractAntCompile antCompile = null

    DependencyManager dependencyManager

    ClasspathConverter classpathConverter = new ClasspathConverter()

    Compile self

    Compile(DefaultProject project, String name) {
        super(project, name)
        actions << this.&compile
        self = this
    }

    protected void compile(Task task) {
        if (!self.antCompile) throw new InvalidUserDataException("The ant compile command must be set!")

        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                self.targetDir, self.sourceDirs)

        if (!self.sourceCompatibility || !self.targetCompatibility) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!")
        }
        
        List classpath = classpathConverter.createFileClasspath(project.rootDir, self.unmanagedClasspath as Object[]) +
                self.dependencyManager.resolveClasspath(name)
        antCompile.execute(existingSourceDirs, self.targetDir, classpath, self.sourceCompatibility,
                self.targetCompatibility, self.options, project.ant)
    }

    Compile with(Object[] args) {
        self.dependencyManager."$configuration" args
        this
    }

    Compile unmanagedClasspath(Object[] elements) {
        self.unmanagedClasspath.addAll((elements as List).flatten())
        this
    }

}