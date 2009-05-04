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
import org.dom4j.tree.DefaultAttribute;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Generates Eclipse configuration files for Eclipse WTP.
 *
 * @author Hans Dockter
 */
public class EclipseWtp extends ConventionTask {
    public static final String WTP_FILE_DIR = ".settings";

    public static final String WTP_FILE_NAME = "org.eclipse.wst.common.component";

    private Object outputDirectory;

    private List<DefaultProjectDependency> projectDependencies;

    private List<Object> warLibs;

    private Map<String, List<Object>> warResourceMappings;

    private Object deployName;

    private boolean failForMissingDependencies = true;

    public EclipseWtp(Project project, String name) {
        super(project, name);

        doFirst(new TaskAction() {
            public void execute(Task task) {
                generateWtp();
            }
        });
    }

    private void generateWtp() {
        File wtpFile = getProject().file(WTP_FILE_DIR + "/" + WTP_FILE_NAME);
        if (wtpFile.exists()) {
            wtpFile.delete();
        }
        if (!wtpFile.getParentFile().exists()) {
            wtpFile.getParentFile().mkdirs();
        }
        try {
            XMLWriter writer = new XMLWriter(new FileWriter(wtpFile), OutputFormat.createPrettyPrint());
            writer.write(createXmlDocument());
            writer.close();

            createFacets(getProject());
        } catch (IOException e) {
            throw new GradleException("Problem when writing Eclipse project file.", e);
        }
    }

    private void createFacets(Project project) {
        Document document = DocumentFactory.getInstance().createDocument();

        EclipseUtil.addFacet(document, "fixed", new DefaultAttribute("facet", "jst.java"));
        EclipseUtil.addFacet(document, "fixed", new DefaultAttribute("facet", "jst.web"));

        EclipseUtil.addFacet(document, "installed", new DefaultAttribute("facet", "jst.java"), new DefaultAttribute("version", "5.0"));
        EclipseUtil.addFacet(document, "installed", new DefaultAttribute("facet", "jst.web"), new DefaultAttribute("version", "2.4"));

        try {
            File facetFile = project.file(".settings/org.eclipse.wst.common.project.facet.core.xml");
            if (facetFile.exists()) {
                facetFile.delete();
            }
            if (!facetFile.getParentFile().exists()) {
                facetFile.getParentFile().mkdirs();
            }
            XMLWriter writer = new XMLWriter(new FileWriter(facetFile), OutputFormat.createPrettyPrint());
            writer.write(document);
            writer.close();
        } catch (IOException e) {
            throw new GradleException("Problem when writing Eclipse project file.", e);
        }
    }

    private Document createXmlDocument() {
        Document document = DocumentFactory.getInstance().createDocument();
        Element root = document.addElement("project-modules").addAttribute("id", "moduleCoreId").addAttribute("project-version", "1.5.0");
        Element wbModule = root.addElement("wb-module").addAttribute("deploy-name", getDeployName().toString());
        wbModule.addElement("property").addAttribute("name", "context-root").addAttribute("value", getDeployName().toString());
        addResourceMappings(wbModule);
        wbModule.addElement("property").addAttribute("name", "java-output-path").addAttribute("value", relativePath(getOutputDirectory()));
        addLibs(wbModule);
        addDependsOnProjects(wbModule);
        return document;
    }

    private void addResourceMappings(Element wbModule) {
        for (String deployPath : getWarResourceMappings().keySet()) {
            for (Object source : getWarResourceMappings().get(deployPath)) {
                wbModule.addElement("wb-resource").
                        addAttribute("deploy-path", deployPath).
                        addAttribute("source-path", relativePath(source));
            }
        }
    }

    private void addLibs(Element wbModule) {
        for (String libPath : EclipseUtil.getSortedStringList(getWarLibs())) {
            wbModule.addElement("dependent-module").
                    addAttribute("deploy-path", "/WEB-INF/lib").
                    addAttribute("handle", "module:/classpath/lib//" + FilenameUtils.separatorsToUnix(libPath)).
                    addElement("dependency-type").setText("uses");
        }
    }

    private void addDependsOnProjects(Element wbModule) {
        for (Project project : EclipseUtil.getDependsOnProjects(getProjectDependencies())) {
            wbModule.addElement("dependent-module").
                    addAttribute("deploy-path", "/WEB-INF/lib").
                    addAttribute("handle", "module:/resource/" + project.getName() + "/" + project.getName()).
                    addElement("dependency-type").setText("uses");
        }
    }

    private String relativePath(Object path) {
        return EclipseUtil.relativePath(getProject(), path);
    }

    /**
     * Returns the java-output-path to be used by the wtp descriptor file.
     *
     * @see #setOutputDirectory(Object)
     */
    public Object getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Sets the java-output-path to be used by the wtp descriptor file.
     *
     * @param outputDirectory An object which toString value is interpreted as a path.
     */
    public void setOutputDirectory(Object outputDirectory) {
        this.outputDirectory = outputDirectory;
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
     * Returns a list with library paths to be deployed as war lib dependencies.
     *
     * @see #setWarLibs(java.util.List)
     */
    public List<Object> getWarLibs() {
        return warLibs;
    }

    /**
     * Sets a list with library paths to be deployed as war lib dependencies.
     *
     * @param warLibs An list with objects which toString value is interpreted as a path
     */
    public void setWarLibs(List<Object> warLibs) {
        this.warLibs = warLibs;
    }

    /**
     * Returns the war resource mappings
     *
     * @see #setWarResourceMappings(java.util.Map)
     */
    public Map<String, List<Object>> getWarResourceMappings() {
        return warResourceMappings;
    }

    /**
     * Maps a deploy-path to source-paths. The assigned source paths must be sub directories of the project root dir.
     * The assigned source path may be described by an absolute or a relative path. A relative path is interpreted as
     * relative to the project root dir. An absolute path is transformed into a relative path in the resulting eclipse file.
     * If an absolute source path is not a sub directory of project root an exception is thrown.
     *
     * @param warResourceMappings
     */
    public void setWarResourceMappings(Map<String, List<Object>> warResourceMappings) {
        this.warResourceMappings = warResourceMappings;
    }

    /**
     * Returns the deploy name for this project.
     *
     * @see #setDeployName(Object)
     */
    public Object getDeployName() {
        return deployName;
    }

    /**
     * Set the deploy name for this project.
     *
     * @param deployName An object which toString value is interpreted as a path.
     */
    public void setDeployName(Object deployName) {
        this.deployName = deployName;
    }

    /**
     * Returns whether the build should fail if lib dependencies intended to be used by this task can not be resolved.
     *
     * @see #setFailForMissingDependencies(boolean)
     */
    public boolean isFailForMissingDependencies() {
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
