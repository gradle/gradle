/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.reporting.dependencies.internal;

import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.reporting.HtmlReportBuilder;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.util.Set;

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
public class HtmlDependencyReporter extends ReportRenderer<Set<Project>, File> {
    private File outputDirectory;
    private final JsonProjectDependencyRenderer renderer;

    public HtmlDependencyReporter(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser) {
        renderer = new JsonProjectDependencyRenderer(versionSelectorScheme, versionComparator, versionParser);
    }

    @Override
    public void render(final Set<Project> projects, File outputDirectory) {
        this.outputDirectory = outputDirectory;

        HtmlReportRenderer renderer = new HtmlReportRenderer();
        renderer.render(projects, new ReportRenderer<Set<Project>, HtmlReportBuilder>() {
            @Override
            public void render(Set<Project> model, HtmlReportBuilder builder) {
                Transformer<String, Project> htmlPageScheme = projectNamingScheme("html");
                Transformer<String, Project> jsScheme = projectNamingScheme("js");
                ProjectPageRenderer projectPageRenderer = new ProjectPageRenderer(jsScheme);
                builder.renderRawHtmlPage("index.html", projects, new ProjectsPageRenderer(htmlPageScheme));
                for (Project project : projects) {
                    String jsFileName = jsScheme.transform(project);
                    generateJsFile(project, jsFileName);
                    String htmlFileName = htmlPageScheme.transform(project);
                    builder.renderRawHtmlPage(htmlFileName, project, projectPageRenderer);
                }

            }

        }, outputDirectory);
    }

    private void generateJsFile(Project project, String fileName) {
        String json = renderer.render(project);
        String content = "var projectDependencyReport = " + json + ";";
        GFileUtils.writeFile(content, new File(outputDirectory, fileName), "utf-8");
    }

    private Transformer<String, Project> projectNamingScheme(final String extension) {
        return project -> toFileName(project, "." + extension);
    }

    private String toFileName(Project project, String extension) {
        String name = project.getPath();
        if (name.equals(":")) {
            return "root" + extension;
        }

        return "root" + name.replace(":", ".") + extension;
    }
}
