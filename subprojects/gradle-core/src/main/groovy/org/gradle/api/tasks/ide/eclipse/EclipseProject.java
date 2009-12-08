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

import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.gradle.api.GradleException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Generates an eclipse <i>.project</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseProject extends ConventionTask {
    public static final String PROJECT_FILE_NAME = ".project";

    private String projectName;

    private ProjectType projectType = ProjectType.SIMPLE;
    private Set<String> natureNames = new LinkedHashSet<String>();
    private Set<String> buildCommandNames = new LinkedHashSet<String>();

    @TaskAction
    protected void generateProject() {
        File projectFile = getProject().file(PROJECT_FILE_NAME);
        try {
            XMLWriter writer = new XMLWriter(new FileWriter(projectFile), OutputFormat.createPrettyPrint());
            writer.write(createXmlDocument());
            writer.close();

        } catch (IOException e) {
            throw new GradleException("Problem when writing Eclipse project file.", e);
        }
    }

    private Document createXmlDocument() {
        Document document = DocumentFactory.getInstance().createDocument();
        Element root = document.addElement("projectDescription");
        root.addElement("name").setText(projectName);
        root.addElement("comment");
        root.addElement("projects");
        addNatures(root);
        addBuildSpec(root);
        return document;
    }

    private void addBuildSpec(Element root) {
        Element buildRoot = root.addElement("buildSpec");

        for (String buildCommandName : this.buildCommandNames) {
            Element buildCommand = buildRoot.addElement("buildCommand");
            buildCommand.addElement("name").setText(buildCommandName);
            buildCommand.addElement("arguments");
        }
    }

    private void addNatures(Element root) {
        Element natures = root.addElement("natures");
        for (String natureName : this.natureNames) {
            natures.addElement("nature").setText(natureName);
        }
    }

    /**
     * Returns the name used for the name of the eclipse project
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Sets the name used for the name of the eclipse project.
     *
     * @param projectName The project name
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Returns the type of the Eclipse project
     */
    public ProjectType getProjectType() {
        return projectType;
    }

    /**
     * Sets the type of the eclipse project
     *
     * @param projectType The project type
     */
    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType;

        this.natureNames.clear();
        this.natureNames.addAll(projectType.natureNames());

        this.buildCommandNames.clear();
        this.buildCommandNames.addAll(projectType.buildCommandNames());
    }

    /**
     * Returns the natures to be added to this Eclipse project.
     */
    public Set<String> getNatureNames() {
        return this.natureNames;
    }

    /**
     * Sets the natures to be added to this Eclipse project.
     *
     * @param natureNames The natures to add.
     */
    public void setNatureNames(Set<String> natureNames) {
        this.natureNames = natureNames;
    }

    /**
     * Returns the build commands to be added to this Eclipse project.
     */
    public Set<String> getBuildCommandNames() {
        return this.buildCommandNames;
    }

    /**
     * Sets the build commands to be added to this Eclipse project.
     *
     * @param buildCommandNames The build commands to add.
     */
    public void setBuildCommandNames(Set<String> buildCommandNames) {
        this.buildCommandNames = buildCommandNames;
    }
}
