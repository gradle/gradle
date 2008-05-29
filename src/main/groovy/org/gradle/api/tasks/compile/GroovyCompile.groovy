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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.GradleUtil

/**
 * @author Hans Dockter
 */
class GroovyCompile extends Compile {
    List groovySourceDirs = []

    AntGroovyc antGroovyCompile = new AntGroovyc()

    GroovyCompile self

    List groovyClasspath

    /**
     * Include pattern for which groovy files should be compiled (e.g. '**&#2F;org/gradle/package1/')).
     * This pattern is added as an nested include the groovyc task.
     */
    List groovyIncludes = []

    /**
     * Exclude pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package2/A*.java').
     * This pattern is added as an nested exclude the groovyc task.
     */
    List groovyExcludes = []

    /**
     * Include pattern for which java files in the joint source folder should be compiled
     * (e.g. '**&#2F;org/gradle/package1/')). This pattern is added as a nested include to the nested javac task of the
     * groovyc task.
     */
    List groovyJavaIncludes = []

    /**
     * Exclude pattern for which java files in the joint source folder should be compiled
     * (e.g. '**&#2F;org/gradle/package2/A*.java'). This pattern is added as a nested exclude to the nested javac task of the
     * groovyc task.
     */
    List groovyJavaExcludes = []

    GroovyCompile(DefaultProject project, String name) {
        super(project, name)
        actions = [this.&compile]
        self = this
    }

    protected void compile(Task task) {
        if (!self.antCompile) throw new InvalidUserDataException("The ant compile command must be set!")
        if (!self.antGroovyCompile) throw new InvalidUserDataException("The ant groovy compile command must be set!")
        if (!self.destinationDir) throw new InvalidUserDataException("The target dir is not set, compile can't be triggered!")

        List existingSourceDirs = existentDirsFilter.findExistingDirs(self.srcDirs)
        List classpath = null
        if (existingSourceDirs) {
            if (!self.sourceCompatibility || !self.targetCompatibility) {
                throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!")
            }
            classpath = createClasspath()
            antCompile.execute(existingSourceDirs, self.includes, self.excludes, self.destinationDir, classpath, self.sourceCompatibility,
                    self.targetCompatibility, self.options, project.ant)
        }
        List existingGroovySourceDirs = existentDirsFilter.findExistingDirs(self.groovySourceDirs)
        if (existingGroovySourceDirs) {
            if (!classpath) {classpath = createClasspath()}
            // todo We need to understand why it is not good enough to put groovy and ant in the task classpath but als Junit. As we don't understand we put the whole testCompile in it right now. It doesn't hurt, but understanding is better :)
            List taskClasspath = GradleUtil.antJarFiles + self.groovyClasspath
            antGroovyCompile.execute(project.ant, existingGroovySourceDirs, self.groovyIncludes, self.groovyExcludes,
                    self.groovyJavaIncludes, self.groovyJavaExcludes, self.destinationDir, classpath, self.sourceCompatibility,
                    self.targetCompatibility, self.options, taskClasspath)
        }
    }

    private List createClasspath() {
        classpathConverter.createFileClasspath(project.rootDir, self.unmanagedClasspath as Object[]) +
                self.dependencyManager.resolveTask(name)
    }

    GroovyCompile groovyInclude(String[] groovyIncludes) {
        this.groovyIncludes += (groovyIncludes as List)
        this
    }

    GroovyCompile groovyExclude(String[] groovyExcludes) {
        this.groovyExcludes += groovyExcludes as List
        this
    }

    GroovyCompile groovyJavaInclude(String[] groovyJavaIncludes) {
        this.groovyJavaIncludes += (groovyJavaIncludes as List)
        this
    }

    GroovyCompile groovyJavaExclude(String[] groovyJavaExcludes) {
        this.groovyJavaExcludes += groovyJavaExcludes as List
        this
    }
}
