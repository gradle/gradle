/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.tasks.ide.eclipse;

import org.apache.commons.io.FilenameUtils;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates an eclipse <i>.classpath</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseClasspath extends ConventionTask {
    public static final String CLASSPATH_FILE_NAME = ".classpath";

    private List srcDirs;

    private List testSrcDirs;

    private Object outputDirectory;

    private Object testOutputDirectory;

    private List<Object> classpathLibs;

    private List<DefaultProjectDependency> projectDependencies;

    private boolean failForMissingDependencies = true;
    private static final String SRC = "src";
    private static final String OUTPUT = "output";
    private static final String CLASSPATHENTRY = "classpathentry";
    private static final String KIND = "kind";
    private static final String PATH = "path";
    private static final String CON = "con";
    private static final String LIB = "lib";
    private static final String CLASSPATH = "classpath";
    private static final String COMBINEACCESSRULES = "combineaccessrules";
    private static final String DEFAULT_JRE_CONTAINER = "org.eclipse.jdt.launching.JRE_CONTAINER";

    public EclipseClasspath(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generateClasspath();
            }
        });
    }

    private void generateClasspath() {
        File eclipseClasspathFile = getProject().file(CLASSPATH_FILE_NAME);

        backupOldClasspathFile(eclipseClasspathFile);

        try {
            XMLWriter writer = new XMLWriter(new FileWriter(eclipseClasspathFile), OutputFormat.createPrettyPrint());
            writer.write(createXmlDocument());
            writer.close();
        } catch (IOException e) {
            throw new GradleException("Problem when writing Eclipse project file.", e);
        }

    }

    private void backupOldClasspathFile(File eclipseClasspathFile) {
        if (eclipseClasspathFile.exists()) {
            GFileUtils.copyFile(eclipseClasspathFile, getProject().file(CLASSPATH_FILE_NAME + ".old"));
        }
    }

    private Document createXmlDocument() {
        Document document = DocumentFactory.getInstance().createDocument();
        Element root = document.addElement(CLASSPATH);

        addSrcDirs(root);
        addTestSrcDirs(root);
        classpathEntry(root, CON, DEFAULT_JRE_CONTAINER);
        addDependsOnProjects(root);
        addLibs(root);

        return document;
    }

    private void addLibs(Element root) {
        for (String path : EclipseUtil.getSortedStringList(getClasspathLibs())) {
            classpathEntry(root, LIB, FilenameUtils.separatorsToUnix(path));
        }
    }

    private void addSrcDirs(Element root) {
        for (String srcDir : existingRelativePaths(getSrcDirs())) {
            classpathEntry(root, SRC, srcDir);
        }
        classpathEntry(root, OUTPUT, relativePath(getOutputDirectory()));
    }

    private void addTestSrcDirs(Element root) {
        for (String testSrcDir : existingRelativePaths(getTestSrcDirs())) {
            classpathEntry(root, SRC, testSrcDir).
                    addAttribute(OUTPUT, FilenameUtils.separatorsToUnix(relativePath(getTestOutputDirectory())));
        }
    }

    private void addDependsOnProjects(Element root) {
        for (Project dependsOnProject : EclipseUtil.getDependsOnProjects(getProjectDependencies())) {
            classpathEntry(root, SRC, "/" + dependsOnProject.getName()).
                    addAttribute(COMBINEACCESSRULES, "false");
        }
    }

    private Element classpathEntry(Element root, String kind, String path) {
        return root.addElement(CLASSPATHENTRY).
                addAttribute(KIND, kind).
                addAttribute(PATH, path);
    }

    private List<String> existingRelativePaths(List<Object> allPaths) {
        List<String> existingRelativePaths = new ArrayList<String>();
        for (Object path : allPaths) {
            if (getProject().file(path).exists()) {
                existingRelativePaths.add(relativePath(path));
            }
        }
        Collections.sort(existingRelativePaths);
        return existingRelativePaths;
    }

    private String relativePath(Object path) {
        return EclipseUtil.relativePath(getProject(), path);
    }

    /**
     * A list of directories which contain the sources. The directories are specified by a relative path to the project root.
     *
     * @return list of directories which contain the sources.
     */
    public List<Object> getSrcDirs() {
        return srcDirs;
    }

    /**
     * Sets a list of paths to be transformed into eclipse source dirs. This list should contain also contain the paths to
     * resources directories. The assigned path may be described by an absolute or a
     * relative path. A relative path is interpreted as relative to the project root dir. An absolute path is transformed
     * into a relative path in the resulting eclipse file. If an absolute source path is not a sub directory of project root an
     * {@link GradleException} is thrown at execution time.
     *
     * @param srcDirs An list with objects which toString value is interpreted as a path
     */
    public void setSrcDirs(List<Object> srcDirs) {
        this.srcDirs = srcDirs;
    }

    /**
     * Returns a list of paths to be transformed into eclipse test source dirs.
     * 
     * @see #getTestSrcDirs() (java.util.List)
     */
    public List getTestSrcDirs() {
        return testSrcDirs;
    }

    /**
     * Sets a list of paths to be transformed into eclipse test source dirs. This list should contain also contain the paths to
     * test resources directories. The assigned path may be described by an absolute or a
     * relative path. A relative path is interpreted as relative to the project root dir. An absolute path is transformed
     * into a relative path in the resulting eclipse file. If an absolute source path is not a sub directory of project root an
     * {@link GradleException} is thrown at execution time.
     *
     * @param testSrcDirs An list with objects which toString value is interpreted as a path
     */
    public void setTestSrcDirs(List testSrcDirs) {
        this.testSrcDirs = testSrcDirs;
    }

    /**
     * Returns the eclipse output directory for compiled sources
     *
     * @see #setOutputDirectory(Object) 
     */
    public Object getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Sets the eclipse output directory for compiled sources. The assigned path may be described by an absolute or a
     * relative path. A relative path is interpreted as relative to the project root dir. An absolute path is transformed
     * into a relative path in the resulting eclipse file. If an absolute source path is not a sub directory of project
     * root an {@link GradleException} is thrown at execution time.
     *
     * @param outputDirectory An object which toString value is interpreted as a path
     */
    public void setOutputDirectory(Object outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns the eclipse output directory for compiled test sources
     *
     * @see #setTestOutputDirectory(Object) 
     */
    public Object getTestOutputDirectory() {
        return testOutputDirectory;
    }

    /**
     * Sets the eclipse output directory for compiled test sources. The assigned path may be described by an absolute or a
     * relative path. A relative path is interpreted as relative to the project root dir. An absolute path is transformed
     * into a relative path in the resulting eclipse file. If an absolute source path is not a sub directory of project root an
     * {@link GradleException} is thrown at execution time.
     *
     * @param testOutputDirectory An object which toString value is interpreted as a path
     */
    public void setTestOutputDirectory(Object testOutputDirectory) {
        this.testOutputDirectory = testOutputDirectory;
    }

    /**
     * Returns the project dependencies to be transformed into eclipse project dependencies.
     *
     * @see #setProjectDependencies(java.util.List)
     */
    public List<DefaultProjectDependency> getProjectDependencies() {
        return projectDependencies;
    }

    /**
     * Sets the project dependencies to be transformed into eclipse project dependencies.
     * 
     * @param projectDependencies
     */
    public void setProjectDependencies(List<DefaultProjectDependency> projectDependencies) {
        this.projectDependencies = projectDependencies;
    }

    /**
     * Returns a list with library paths to be transformed into eclipse lib dependencies.
     *
     * @see #setClasspathLibs(java.util.List)
     */
    public List<Object> getClasspathLibs() {
        return classpathLibs;
    }

    /**
     * Sets a list with library paths to be transformed into eclipse lib dependencies.
     *
     * @param classpathLibs An list with objects which toString value is interpreted as a path
     */
    public void setClasspathLibs(List<Object> classpathLibs) {
        this.classpathLibs = classpathLibs;
    }

    /**
     * Returns whether the build should fail if lib dependencies intended to be used by this task can not be resolved.
     *
     * @see #setFailForMissingDependencies(boolean)
     */
    public boolean getFailForMissingDependencies() {
        return failForMissingDependencies;
    }

    /**
     * Sets whether the build should fail if lib dependencies intended to be used by this task can not be resolved.
     * It is important to note that this task does not do any resolve. The purpose of this property is to inform
     * for example a plugin which configures this task. This plugin is supposed to make the build fail, if not
     * all the dependencies it intends to assign to this task can be resolved.
     *
     * @param failForMissingDependencies
     */
    public void setFailForMissingDependencies(boolean failForMissingDependencies) {
        this.failForMissingDependencies = failForMissingDependencies;
    }
}
