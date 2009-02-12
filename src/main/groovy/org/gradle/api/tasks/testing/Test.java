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

package org.gradle.api.tasks.testing;

import org.gradle.api.*;
import org.gradle.api.artifacts.ConfigurationResolveInstructionModifier;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.compile.ClasspathConverter;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * A task for executin Junit 3.8.x and Junit 4 tests.
 * 
 * @author Hans Dockter
 */
public class Test extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(Test.class);

    private File testClassesDir = null;

    private File testResultsDir = null;

    private File testReportDir = null;

    private JunitOptions options = new JunitOptions();

    private List<String> includes;

    private List<String> excludes;

    private boolean stopAtFailuresOrErrors = true;

    private List unmanagedClasspath = null;

    private AntJunit antJunit = new AntJunit();

    private DependencyManager dependencyManager = null;

    private ConfigurationResolveInstructionModifier resolveInstructionModifier;

    protected ExistingDirsFilter existingDirsFilter = new ExistingDirsFilter();

    protected ClasspathConverter classpathConverter = new ClasspathConverter();

    public Test(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                executeTests(task);
            }
        });
    }

    protected void executeTests(Task task) {
        if (getTestClassesDir() == null) throw new InvalidUserDataException(
                "The testClassesDir property is not set, testing can't be triggered!");
        if (getTestResultsDir() == null) throw new InvalidUserDataException(
                "The testResultsDir property is not set, testing can't be triggered!");

        existingDirsFilter.checkExistenceAndThrowStopActionIfNot(getTestClassesDir());

        antJunit.execute(getTestClassesDir(), getClasspath(), getTestResultsDir(), getTestReportDir(), includes,
                excludes, options, getProject().getAnt());
        if (stopAtFailuresOrErrors && GUtil.isTrue(getProject().getAnt().getProject().getProperty(AntJunit.FAILURES_OR_ERRORS_PROPERTY))) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }

    public List getClasspath() {
        List classpath = classpathConverter.createFileClasspath(getProject().getRootDir(),
                GUtil.addLists(WrapUtil.toList(getTestClassesDir()), getUnmanagedClasspath(),
                        getDependencyManager().configuration(resolveInstructionModifier.getConfiguration()).resolve(resolveInstructionModifier)));
        return classpath;
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setIncludes(java.util.List) 
     */
    public Test include(String... includes) {
        this.includes = GUtil.chooseCollection(this.includes, getExcludes());
        this.includes.addAll(Arrays.asList(includes));
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setExcludes(java.util.List) (java.util.List)
     */
    public Test exclude(String... excludes) {
        this.excludes = GUtil.chooseCollection(this.excludes, getExcludes());
        this.excludes.addAll(Arrays.asList(excludes));
        return this;
    }

    /**
     * This methode is usually used only internally by Gradle.
     * A list of files are added to the compile classpath. The files should point to jars or directories containing
     * class files. The files added here are not shared in a multi-project build and are not listed in
     * a dependency descriptor if you upload your library to a repository.
     * @param elements The elements to be added
     * @return this
     */
    public Test unmanagedClasspath(Object... elements) {
        this.unmanagedClasspath = GUtil.chooseCollection(this.unmanagedClasspath, getUnmanagedClasspath());
        unmanagedClasspath.addAll(GUtil.flatten(Arrays.asList(elements)));
        return this;
    }

    /**
     * Returns the root folder for the compiled test sources. 
     */
    public File getTestClassesDir() {
        return (File) conv(testClassesDir, "testClassesDir");
    }

    /**
     * Sets the root folder for the compiled test sources.
     * 
     * @param testClassesDir The root folder
     */
    public void setTestClassesDir(File testClassesDir) {
        this.testClassesDir = testClassesDir;
    }

    /**
     * Returns the root folder for the test results.
     */
    public File getTestResultsDir() {
        return (File) conv(testResultsDir, "testResultsDir");
    }

    /**
     * Sets the root folder for the test results.
     * 
     * @param testResultsDir The root folder
     */
    public void setTestResultsDir(File testResultsDir) {
        this.testResultsDir = testResultsDir;
    }

    /**
     * Returns the root folder for the test reports.
     */
    public File getTestReportDir() {
        return (File) conv(testReportDir, "testReportDir");
    }

    /**
     * Sets the root folder for the test reports.
     *
     * @param testReportDir The root folder
     */
    public void setTestReportDir(File testReportDir) {
        this.testReportDir = testReportDir;
    }

    /**
     * Returns an JUnit option instance to read or modify.
     * @return
     */
    public JunitOptions getOptions() {
        return options;
    }

    /**
     * Sets a new JUnit options instance. It is usually not necessary to set a new instance.
     * 
     * @param options The new instance
     */
    public void setOptions(JunitOptions options) {
        this.options = options;
    }

    /**
     * Returns the include patterns for test execution.
     * 
     * @see #include(String[])
     */
    public List getIncludes() {
        return (List) conv(includes, "includes");
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see #include(String[])
     */
    public void setIncludes(List includes) {
        this.includes = includes;
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see #include(String[])
     */
    public List getExcludes() {
        return (List) conv(excludes, "excludes");
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see #exclude(String[])
     */
    public void setExcludes(List excludes) {
        this.excludes = excludes;
    }

    /**
     * Returns whether this task should throw an exception in case of test failuer or error.
     */
    public boolean isStopAtFailuresOrErrors() {
        return stopAtFailuresOrErrors;
    }

    /**
     * Sets whether this task should throw an exception in case of test failuer or error.
     */
    public void setStopAtFailuresOrErrors(boolean stopAtFailuresOrErrors) {
        this.stopAtFailuresOrErrors = stopAtFailuresOrErrors;
    }

    /**
     * Returns the unmanaged classpath.
     *
     * @see #unmanagedClasspath(Object[])  
     */
    public List getUnmanagedClasspath() {
        return (List) conv(unmanagedClasspath, "unmanagedClasspath");
    }

    /**
     * Sets the unmanaged classpath.
     *
     * @see #unmanagedClasspath(Object[])
     */
    public void setUnmanagedClasspath(List unmanagedClasspath) {
        this.unmanagedClasspath = unmanagedClasspath;
    }

    /**
     * Returns the AntJunit instance the test execution is delegated to.
     */
    public AntJunit getAntJunit() {
        return antJunit;
    }

    /**
     * Sets the AntJunit instance the test execution is delegated to.
     * 
     * @param antJunit The new instance
     */
    public void setAntJunit(AntJunit antJunit) {
        this.antJunit = antJunit;
    }

    /**
     * Returns the dependency manager used by this task for resolving dependencies.
     */
    public DependencyManager getDependencyManager() {
        return (DependencyManager) conv(dependencyManager, "dependencyManager");
    }

    /**
     * Sets the dependency manager used by this task for resolving dependencies.
     * 
     * @param dependencyManager The new dependency manager
     */
    public void setDependencyManager(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    public ConfigurationResolveInstructionModifier getResolveInstruction() {
        return resolveInstructionModifier;
    }

    public void setResolveInstruction(ConfigurationResolveInstructionModifier resolveInstructionModifier) {
        this.resolveInstructionModifier = resolveInstructionModifier;
    }
}
