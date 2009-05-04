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
import org.dom4j.tree.DefaultAttribute;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Generates Eclipse configuration files for Eclipse WTP modules
 *
 * @author Phil Messenger
 */
public class EclipseWtpModule extends ConventionTask {

    private List<Object> srcDirs;

    public EclipseWtpModule(Project project, String name) {
        super(project, name);

        doFirst(new TaskAction() {
            public void execute(Task task) {
                generateWtpModule();
            }
        });
    }

    private void generateWtpModule() {
        File wtpFile = getProject().file(EclipseWtp.WTP_FILE_DIR + "/" + EclipseWtp.WTP_FILE_NAME);
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
        EclipseUtil.addFacet(document, "fixed", new DefaultAttribute("facet", "jst.utility"));

        EclipseUtil.addFacet(document, "installed", new DefaultAttribute("facet", "jst.java"), new DefaultAttribute("version", "5.0"));
        EclipseUtil.addFacet(document, "installed", new DefaultAttribute("facet", "jst.utility"), new DefaultAttribute("version", "1.0"));

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
        Element wbModule = root.addElement("wb-module").addAttribute("deploy-name", getProject().getName());
        addResourceMappings(wbModule);
        return document;
    }

    private void addResourceMappings(Element wbModule) {
        for (Object srcDir : getSrcDirs()) {
            if (getProject().file(srcDir.toString()).exists()) {
                wbModule.addElement("wb-resource").addAttribute("deploy-path", "/").addAttribute("source-path", EclipseUtil.relativePath(getProject(), srcDir.toString()));
            }
        }
    }

    public void setSrcDirs(List<Object> srcDirs) {
        this.srcDirs = srcDirs;
    }

    public List<Object> getSrcDirs() {
        return srcDirs;
    }
}
