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

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails.ProjectNameAndPath;
import org.gradle.api.tasks.diagnostics.internal.ProjectsWithConfigurations;
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
 *
 * @see JsonProjectDependencyRenderer
 */
public class HtmlDependencyReporter extends ReportRenderer<ProjectsWithConfigurations<ProjectNameAndPath, ConfigurationDetails>, File> {
    private File outputDirectory;
    private final JsonProjectDependencyRenderer renderer;

    public HtmlDependencyReporter(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser) {
        renderer = new JsonProjectDependencyRenderer(versionSelectorScheme, versionComparator, versionParser);
    }

    @Override
    public void render(final ProjectsWithConfigurations<ProjectNameAndPath, ConfigurationDetails> projectsWithConfigurations, File outputDirectory) {
        this.outputDirectory = outputDirectory;

        HtmlReportRenderer renderer = new HtmlReportRenderer();
        renderer.render(projectsWithConfigurations.getProjects(), new ReportRenderer<Set<ProjectNameAndPath>, HtmlReportBuilder>() {
            @Override
            public void render(Set<ProjectNameAndPath> model, HtmlReportBuilder builder) {
                Transformer<String, ProjectNameAndPath> htmlPageScheme = projectNamingScheme("html");
                Transformer<String, ProjectNameAndPath> jsScheme = projectNamingScheme("js");
                ProjectPageRenderer projectPageRenderer = new ProjectPageRenderer(jsScheme);
                builder.renderRawHtmlPage("index.html", model, new ProjectsPageRenderer(htmlPageScheme));
                for (ProjectNameAndPath project : model) {
                    String jsFileName = jsScheme.transform(project);
                    generateJsFile(project, projectsWithConfigurations.getConfigurationsFor(project), jsFileName);
                    String htmlFileName = htmlPageScheme.transform(project);
                    builder.renderRawHtmlPage(htmlFileName, project, projectPageRenderer);
                }

            }

        }, outputDirectory);
    }

    private void generateJsFile(ProjectNameAndPath project, Iterable<ConfigurationDetails> configurations, String fileName) {
        String json = renderer.render(project, configurations);
        String content = "var projectDependencyReport = " + json + ";";
        GFileUtils.writeFile(content, new File(outputDirectory, fileName), "utf-8");
    }

    private Transformer<String, ProjectNameAndPath> projectNamingScheme(final String extension) {
        return project -> toFileName(project, "." + extension);
    }

    private String toFileName(ProjectNameAndPath project, String extension) {
        String name = project.getPath();
        if (name.equals(":")) {
            return "root" + extension;
        }

        return "root" + name.replace(":", ".") + extension;
    }
}
