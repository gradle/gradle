/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.profile;

import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;
import org.gradle.reporting.TabbedPageRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class ProfileReportRenderer {

    public void writeTo(BuildProfile buildProfile, File file) {
        HtmlReportRenderer renderer = new HtmlReportRenderer();
        renderer.renderSinglePage(buildProfile, new ProfilePageRenderer(), file);
    }

    private static class ProfilePageRenderer extends TabbedPageRenderer<BuildProfile> {
        private static final URL STYLE_URL = ProfilePageRenderer.class.getResource("style.css");

        @Override
        protected String getTitle() {
            return "Profile report";
        }

        @Override
        protected URL getStyleUrl() {
            return STYLE_URL;
        }

        @Override
        protected ReportRenderer<BuildProfile, SimpleHtmlWriter> getHeaderRenderer() {
            return new ReportRenderer<BuildProfile, SimpleHtmlWriter>() {
                @Override
                public void render(BuildProfile model, SimpleHtmlWriter htmlWriter) throws IOException {
                    htmlWriter.startElement("div").attribute("id", "header")
                        .startElement("p").characters(model.getBuildDescription()).endElement()
                        .startElement("p").characters(model.getBuildStartedDescription()).endElement()
                    .endElement();
                }
            };
        }

        @Override
        protected  ReportRenderer<BuildProfile, SimpleHtmlWriter> getContentRenderer() {
            return new ReportRenderer<BuildProfile, SimpleHtmlWriter>() {
                @Override
                public void render(BuildProfile model, SimpleHtmlWriter htmlWriter) throws IOException {
                    CompositeOperation<Operation> profiledProjectConfiguration = model.getProjectConfiguration();

                    htmlWriter.startElement("div").attribute("id", "tabs")
                        .startElement("ul").attribute("class", "tabLinks")
                            .startElement("li").startElement("a").attribute("href", "#tab0").characters("Summary").endElement().endElement()
                            .startElement("li").startElement("a").attribute("href", "#tab1").characters("Configuration").endElement().endElement()
                            .startElement("li").startElement("a").attribute("href", "#tab2").characters("Dependency Resolution").endElement().endElement()
                            .startElement("li").startElement("a").attribute("href", "#tab3").characters("Artifact Transforms").endElement().endElement()
                            .startElement("li").startElement("a").attribute("href", "#tab4").characters("Task Execution").endElement().endElement()
                        .endElement();
                        htmlWriter.startElement("div").attribute("class", "tab").attribute("id", "tab0");
                            htmlWriter.startElement("h2").characters("Summary").endElement();
                            htmlWriter.startElement("table");
                                htmlWriter.startElement("thead");
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("th").characters("Description").endElement();
                                        htmlWriter.startElement("th").attribute("class", "numeric").characters("Duration").endElement();
                                    htmlWriter.endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Total Build Time").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedTotal())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Startup").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedStartup())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Settings and buildSrc").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedSettings())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Loading Projects").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedProjectsLoading())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Configuring Projects").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(profiledProjectConfiguration.getElapsedTime())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Artifact Transforms").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedArtifactTransformTime())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Task Execution").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedTotalExecutionTime())).endElement();
                                htmlWriter.endElement();
                            htmlWriter.endElement();
                        htmlWriter.endElement();
                        htmlWriter.startElement("div").attribute("class", "tab").attribute("id", "tab1");
                            htmlWriter.startElement("h2").characters("Configuration").endElement();
                            htmlWriter.startElement("table");
                                htmlWriter.startElement("thead");
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("th").characters("Project").endElement();
                                        htmlWriter.startElement("th").attribute("class", "numeric").characters("Duration").endElement();
                                    htmlWriter.endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("All projects").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(profiledProjectConfiguration.getElapsedTime())).endElement();
                                htmlWriter.endElement();
                                for (Operation operation : profiledProjectConfiguration) {
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("td").characters(operation.getDescription()).endElement();
                                        htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(operation.getElapsedTime())).endElement();
                                    htmlWriter.endElement();
                                }
                            htmlWriter.endElement()
                        .endElement();
                        htmlWriter.startElement("div").attribute("class", "tab").attribute("id", "tab2");
                            htmlWriter.startElement("h2").characters("Dependency Resolution").endElement()
                                .startElement("table")
                                    .startElement("thead");
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("th").characters("Dependencies").endElement();
                                        htmlWriter.startElement("th").attribute("class", "numeric").characters("Duration").endElement();
                                    htmlWriter.endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("All dependencies").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getDependencySets().getElapsedTime())).endElement();
                                htmlWriter.endElement();

                                for (Operation operation : model.getDependencySets()) {
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("td").characters(operation.getDescription()).endElement();
                                        htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(operation.getElapsedTime())).endElement();
                                    htmlWriter.endElement();
                                }
                            htmlWriter.endElement()
                        .endElement();
                        htmlWriter.startElement("div").attribute("class", "tab").attribute("id", "tab3");
                            htmlWriter.startElement("h2").characters("Artifact Transforms").endElement()
                                .startElement("table")
                                    .startElement("thead");
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("th").characters("Transform").endElement();
                                        htmlWriter.startElement("th").attribute("class", "numeric").characters("Duration").endElement();
                                    htmlWriter.endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("All transforms").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(model.getElapsedArtifactTransformTime())).endElement();
                                htmlWriter.endElement();

                                for (Operation operation : model.getTransformations()) {
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("td").characters(operation.getDescription()).endElement();
                                        htmlWriter.startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(operation.getElapsedTime())).endElement();
                                    htmlWriter.endElement();
                                }
                            htmlWriter.endElement()
                        .endElement();
                        htmlWriter.startElement("div").attribute("class", "tab").attribute("id", "tab4");
                            htmlWriter.startElement("h2").characters("Task Execution").endElement()
                            .startElement("table")
                                .startElement("thead")
                                    .startElement("tr")
                                        .startElement("th").characters("Task").endElement()
                                        .startElement("th").attribute("class", "numeric").characters("Duration").endElement()
                                        .startElement("th").characters("Result").endElement()
                                    .endElement()
                                .endElement();
                                for (ProjectProfile project : model.getProjects()) {
                                   htmlWriter.startElement("tr")
                                        .startElement("td").characters(project.getPath()).endElement()
                                        .startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(project.getElapsedTime())).endElement()
                                        .startElement("td").characters("(total)").endElement()
                                    .endElement();
                                    for (TaskExecution taskExecution : project.getTasks()) {
                                        htmlWriter.startElement("tr")
                                            .startElement("td").attribute("class", "indentPath").characters(taskExecution.getPath()).endElement()
                                            .startElement("td").attribute("class", "numeric").characters(TimeFormatting.formatDurationVeryTerse(taskExecution.getElapsedTime())).endElement()
                                            .startElement("td").characters(taskExecution.getStatus()).endElement()
                                        .endElement();
                                    }
                                }
                            htmlWriter.endElement()
                        .endElement()
                    .endElement();
                }
            };
        }
    }
}
