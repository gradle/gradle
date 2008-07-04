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
import org.gradle.api.Project
import org.gradle.api.TaskAction

/**
 * @author Hans Dockter
 */
class GroovyCompile extends Compile {
    List groovySourceDirs = null

    AntGroovyc antGroovyCompile = new AntGroovyc()

    List groovyClasspath

    /**
     * Include pattern for which groovy files should be compiled (e.g. '**&#2F;org/gradle/package1/')).
     * This pattern is added as an nested include the groovyc task.
     */
    List groovyIncludes = null

    /**
     * Exclude pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package2/A*.java').
     * This pattern is added as an nested exclude the groovyc task.
     */
    List groovyExcludes = null

    /**
     * Include pattern for which java files in the joint source folder should be compiled
     * (e.g. '**&#2F;org/gradle/package1/')). This pattern is added as a nested include to the nested javac task of the
     * groovyc task.
     */
    List groovyJavaIncludes = null

    /**
     * Exclude pattern for which java files in the joint source folder should be compiled
     * (e.g. '**&#2F;org/gradle/package2/A*.java'). This pattern is added as a nested exclude to the nested javac task of the
     * groovyc task.
     */
    List groovyJavaExcludes = null

    GroovyCompile(Project project, String name) {
        super(project, name)
        // Groovy Mambo Jambo. Let's refactor to Java soon.
        setActions(new ArrayList<TaskAction>())
        doFirst(this.&compile)
    }

    protected void compile(Task task) {
        if (!antCompile) throw new InvalidUserDataException("The ant compile command must be set!")
        if (!getAntGroovyCompile()) throw new InvalidUserDataException("The ant groovy compile command must be set!")
        if (!getDestinationDir()) throw new InvalidUserDataException("The target dir is not set, compile can't be triggered!")

        List existingSourceDirs = existentDirsFilter.findExistingDirs(getSrcDirs())
        List classpath = null
        if (existingSourceDirs) {
            if (!getSourceCompatibility() || !getTargetCompatibility()) {
                throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!")
            }
            classpath = createClasspath()
            antCompile.execute(existingSourceDirs, getIncludes(), getExcludes(), getDestinationDir(), classpath, getSourceCompatibility(),
                    getTargetCompatibility(), getOptions(), project.ant)
        }
        List existingGroovySourceDirs = existentDirsFilter.findExistingDirs(getGroovySourceDirs())
        if (existingGroovySourceDirs) {
            if (!classpath) {classpath = createClasspath()}
            // todo We need to understand why it is not good enough to put groovy and ant in the task classpath but also Junit. As we don't understand we put the whole testCompile in it right now. It doesn't hurt, but understanding is better :)
            List taskClasspath = GradleUtil.antJarFiles + getGroovyClasspath()
            antGroovyCompile.execute(project.ant, existingGroovySourceDirs, getGroovyIncludes(), getGroovyExcludes(),
                    getGroovyJavaIncludes(), getGroovyExcludes(), getDestinationDir(), classpath, getSourceCompatibility(),
                    getTargetCompatibility(), getOptions(), taskClasspath)
        }
    }

    private List createClasspath() {
        classpathConverter.createFileClasspath(project.rootDir, getUnmanagedClasspath()) +
                getDependencyManager().resolveTask(name)
    }

    GroovyCompile groovyInclude(String[] groovyIncludes) {
        if (getGroovyIncludes() == null) {
            this.groovyIncludes = new ArrayList()
        }
        this.groovyIncludes += (groovyIncludes as List)
        this
    }

    GroovyCompile groovyExclude(String[] groovyExcludes) {
        if (getGroovyExcludes() == null) {
            this.groovyExcludes = new ArrayList()
        }
        this.groovyExcludes += groovyExcludes as List
        this
    }

    GroovyCompile groovyJavaInclude(String[] groovyJavaIncludes) {
        if (getGroovyJavaIncludes() == null) {
            this.groovyJavaIncludes = new ArrayList()
        }
        this.groovyJavaIncludes += (groovyJavaIncludes as List)
        this
    }

    GroovyCompile groovyJavaExclude(String[] groovyJavaExcludes) {
        if (getGroovyJavaExcludes() == null) {
            this.groovyJavaExcludes = new ArrayList()
        }
        this.groovyJavaExcludes += groovyJavaExcludes as List
        this
    }

    public List getGroovyClasspath() {
        return conv(groovyClasspath, "groovyClasspath");
    }

    public void setGroovyClasspath(List groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    public AntGroovyc getAntGroovyCompile() {
        return antGroovyCompile;
    }

    public void setAntGroovyCompile(AntGroovyc antGroovyCompile) {
        this.antGroovyCompile = antGroovyCompile;
    }

    public List getGroovySourceDirs() {
        return conv(groovySourceDirs, "groovySourceDirs");
    }

    public void setGroovySourceDirs(List groovySourceDirs) {
        this.groovySourceDirs = groovySourceDirs;
    }

    public List getGroovyIncludes() {
        return conv(groovyIncludes, "groovyIncludes");
    }

    public void setGroovyIncludes(List groovyIncludes) {
        this.groovyIncludes = groovyIncludes;
    }

    public List getGroovyExcludes() {
        return conv(groovyExcludes, "groovyExcludes");
    }

    public void setGroovyExcludes(List groovyExcludes) {
        this.groovyExcludes = groovyExcludes;
    }

    public List getGroovyJavaIncludes() {
        return conv(groovyJavaIncludes, "groovyJavaIncludes");
    }

    public void setGroovyJavaIncludes(List groovyJavaIncludes) {
        this.groovyJavaIncludes = groovyJavaIncludes;
    }

    public List getGroovyJavaExcludes() {
        return conv(groovyJavaExcludes, "groovyJavaExcludes");
    }

    public void setGroovyJavaExcludes(List groovyJavaExcludes) {
        this.groovyJavaExcludes = groovyJavaExcludes;
    }
}
