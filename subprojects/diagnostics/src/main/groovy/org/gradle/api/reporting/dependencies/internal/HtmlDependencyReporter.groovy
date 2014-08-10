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
import org.gradle.reporting.HtmlReportBuilder
import org.gradle.reporting.HtmlReportRenderer
import org.gradle.reporting.ReportRenderer
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
 *
 * @see JsonProjectDependencyRenderer
 */
class HtmlDependencyReporter extends ReportRenderer<Set<Project>, File> {
    private File outputDirectory;
    private final JsonProjectDependencyRenderer renderer

    HtmlDependencyReporter(VersionMatcher versionMatcher) {
        renderer = new JsonProjectDependencyRenderer(versionMatcher)
    }

    @Override
    void render(Set<Project> projects, File outputDirectory) {
        this.outputDirectory = outputDirectory

        def renderer = new HtmlReportRenderer()
        renderer.render(projects, new ReportRenderer<Set<Project>, HtmlReportBuilder>() {
            @Override
            void render(Set<Project> model, HtmlReportBuilder builder) {
                def htmlPageScheme = projectNamingScheme("html")
                def jsScheme = projectNamingScheme("js")
                def projectPageRenderer = new ProjectPageRenderer(jsScheme)
                builder.renderRawHtmlPage("index.html", projects, new ProjectsPageRenderer(htmlPageScheme))
                for (Project project : projects) {
                    String jsFileName = jsScheme.transform(project)
                    generateJsFile(project, jsFileName)
                    String htmlFileName = htmlPageScheme.transform(project)
                    builder.renderRawHtmlPage(htmlFileName, project, projectPageRenderer)
                }
            }
        }, outputDirectory)
    }

    private void generateJsFile(Project project, String fileName) {
        String json = renderer.render(project)
        String content = "var projectDependencyReport = " + json + ";";
        GFileUtils.writeFile(content, new File(outputDirectory, fileName), "utf-8")
    }

    private Transformer<String, Project> projectNamingScheme(String extension) {
        new Transformer<String, Project>() {
            String transform(Project project) {
                toFileName(project, "." + extension)
            }
        }
    }

    private String toFileName(Project project, String extension) {
        String name = project.path
        if (name.equals(':')) {
            return "root" + extension
        }
        return "root" + name.replace(':', '.') + extension;
    }
}
