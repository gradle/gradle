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
class Compile extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Compile)

    /**
     * The directories with the sources to compile
     */
    List srcDirs = []

    /**
     * The directory where to put the compiled classes (.class files)
     */
    File destinationDir

    /**
     * The sourceCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    String sourceCompatibility

    /**
     * The targetCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    String targetCompatibility

    /**
     * This property is used internally by Gradle. It is usually not used by build scripts.
     * A list of files added to the compile classpath. The files should point to jars or directories containing
     * class files. The files added here are not shared in a multi-project build and are not mentioned in
     * a dependency descriptor if you upload your library to a repository.
     */
    List unmanagedClasspath = []

    /**
     * Options for the compiler. The compile is delegated to the ant javac task. This property contains almost
     * all of the properties available for the ant javac task.
     */
    CompileOptions options = new CompileOptions()

    /**
     * Include pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package1/')).
     */
    List includes = []

    /**
     * Exclude pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package2/A*.java').
     */
    List excludes = []

    protected ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter()

    protected AntJavac antCompile = new AntJavac()

    protected DependencyManager dependencyManager

    protected ClasspathConverter classpathConverter = new ClasspathConverter()

    protected Compile self

    Compile(DefaultProject project, String name) {
        super(project, name)
        actions << this.&compile
        self = this
    }

    protected void compile(Task task) {
        if (!self.antCompile) throw new InvalidUserDataException("The ant compile command must be set!")

        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                self.destinationDir, self.srcDirs)

        if (!self.sourceCompatibility || !self.targetCompatibility) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!")
        }
        
        List classpath = classpathConverter.createFileClasspath(project.rootDir, self.unmanagedClasspath as Object[]) +
                self.dependencyManager.resolveTask(name)
        antCompile.execute(existingSourceDirs, includes, excludes, self.destinationDir, classpath, self.sourceCompatibility,
                self.targetCompatibility, self.options, project.ant)
    }

    /**
     * Add the elements to the unmanaged classpath.
     */
    Compile unmanagedClasspath(Object[] elements) {
        self.unmanagedClasspath.addAll((elements as List).flatten())
        this
    }

    Compile include(String[] includes) {
        this.includes += (includes as List)
        this
    }

    Compile exclude(String[] excludes) {
        this.excludes += excludes as List
        this
    }

}