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
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Generates an eclipse <i>.project</i> file.
 *
 * @author Hans Dockter
 */
public class EclipseProject extends ConventionTask {
    public static final String PROJECT_FILE_NAME = ".project";

    private String projectName;

    private ProjectType projectType = ProjectType.SIMPLE;

    public EclipseProject(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generateProject(task);
            }
        });
    }

    private void generateProject(Task task) {
        File projectFile = task.getProject().file(PROJECT_FILE_NAME);
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
        addBuildSpec(root);
        addNatures(root);
        return document;
    }

    private void addBuildSpec(Element root) {
        Element natures = root.addElement("natures");
        for (String natureName : projectType.natureNames()) {
            natures.addElement("nature").setText(natureName);
        }
    }

    private void addNatures(Element root) {
        Element buildRoot = root.addElement("buildSpec");

        for (String buildCommandName : projectType.buildCommandNames()) {
            Element buildCommand = buildRoot.addElement("buildCommand");
            buildCommand.addElement("name").setText(buildCommandName);
            buildCommand.addElement("arguments");
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
    }
}
