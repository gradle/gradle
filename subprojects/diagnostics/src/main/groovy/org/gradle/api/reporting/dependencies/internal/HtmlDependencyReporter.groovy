/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.reporting.dependencies.internal

import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.util.GFileUtils

/**
 * Class responsible for the generation of an HTML dependency report.
 * <p>
 * The strategy is the following. The reporter uses an HTML template file containing a
 * placeholder <code>@js@</code>. For every project, it generates a JSON structure containing
 * all the data that must be displayed by the report. A JS file declaring a single variable, containing
 * this JSON structure, is then generated for the project. An HTML file is then generated from the template,
 * by replacing a placeholder @js@ by the name of the generated JS file.
 * The HTML file uses a JavaScript script to generate an interactive page from the data contained in
 * the JSON structure.
 * <p>
 * The same technique is also used to generate the index report, listing all the projects for which
 * a dependency report has been generated.
 *
 * @see JsonDependencyReportIndexRenderer
 * @see JsonProjectDependencyRenderer
 */
class HtmlDependencyReporter {

    File outputDirectory;
    JsonProjectDependencyRenderer renderer
    JsonDependencyReportIndexRenderer indexRenderer = new JsonDependencyReportIndexRenderer()

    HtmlDependencyReporter(VersionMatcher versionMatcher) {
        renderer = new JsonProjectDependencyRenderer(versionMatcher)
    }

    /**
     * Sets the output directory of the report. This directory contains the generated HTML file,
     * but also JS and CSS files. This method must be called before generating the report.
     */
    void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory
    }

    /**
     * Generates a report for each of the given projects, and generates the index report
     */
    void generate(Set<Project> projects) throws IOException {
        GFileUtils.copyURLToFile(getClass().getResource("/org/gradle/reporting/base-style.css"), new File(outputDirectory, "base-style.css"))
        copyReportFile("d.gif")
        copyReportFile("d.png")
        copyReportFile("jquery.jstree.js")
        copyReportFile("jquery-1.10.1.min.js")
        copyReportFile("script.js")
        copyReportFile("style.css")
        copyReportFile("throbber.gif")
        copyReportFile("tree.css")
        copyReportFile("index.html")

        String template = readHtmlTemplate();
        for (Project project : projects) {
            String jsFileName = toFileName(project, '.js')
            generateJsFile(project, jsFileName)
            String htmlFileName = toFileName(project, '.html')
            generateHtmlFile(template, htmlFileName, jsFileName)
        }

        generateIndexJsFile(projects, 'index.js')
    }

    private void generateJsFile(Project project, String fileName) {
        String json = renderer.render(project)
        String content = "var projectDependencyReport = " + json + ";";
        GFileUtils.writeFile(content, new File(outputDirectory, fileName), "utf-8")
    }

    private void generateIndexJsFile(Set<Project> projects, String fileName) {
        String json = indexRenderer.render(projects, new Transformer<String, Project>() {
            String transform(Project project) {
                toFileName(project, ".html")
            }
        })

        String content = "var mainDependencyReport = " + json.toString() + ";";
        GFileUtils.writeFile(content, new File(outputDirectory, fileName), "utf-8")
    }

    private void generateHtmlFile(String template, String fileName, String jsFileName) {
        String content = template.replace('@js@', jsFileName);
        GFileUtils.writeFile(content, new File(outputDirectory, fileName), "utf-8")
    }

    private copyReportFile(String fileName) {
        GFileUtils.copyURLToFile(getClass().getResource(getReportResourcePath(fileName)),
                                 new File(outputDirectory, fileName))

    }

    private String readHtmlTemplate() {
        getClass().getResourceAsStream(getReportResourcePath("template.html")).getText("UTF8")
    }

    private String getReportResourcePath(String fileName) {
        "/org/gradle/api/tasks/diagnostics/htmldependencyreport/" + fileName
    }

    private String toFileName(Project project, String extension) {
        String name = project.path
        if (name.equals(':')) {
            return "root" + extension
        }
        return "root" + name.replace(':', '.') + extension;
    }
}
