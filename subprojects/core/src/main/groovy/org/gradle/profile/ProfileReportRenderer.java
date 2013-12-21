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

import org.gradle.api.internal.html.SimpleHtmlWriter;
import org.gradle.reporting.DurationFormatter;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;
import org.gradle.reporting.TabbedPageRenderer;

import java.io.File;
import java.io.IOException;

public class ProfileReportRenderer {
    public void writeTo(BuildProfile buildProfile, File file) {
        HtmlReportRenderer renderer = new HtmlReportRenderer();
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/base-style.css"));
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/report.js"));
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/css3-pie-1.0beta3.htc"));
        renderer.requireResource(getClass().getResource("style.css"));
        renderer.renderer(new ProfilePageRenderer()).writeTo(buildProfile, file);
    }
    private static final DurationFormatter DURATION_FORMAT = new DurationFormatter();

    private static class ProfilePageRenderer extends TabbedPageRenderer<BuildProfile> {
        @Override
        protected String getTitle() {
            return "Profile report";
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
                            .startElement("li").startElement("a").attribute("href", "#tab3").characters("Task Execution").endElement().endElement()
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
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(model.getElapsedTotal())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Startup").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(model.getElapsedStartup())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Settings and BuildSrc").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(model.getElapsedSettings())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Loading Projects").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(model.getElapsedProjectsLoading())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Configuring Projects").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(profiledProjectConfiguration.getElapsedTime())).endElement();
                                htmlWriter.endElement();
                                htmlWriter.startElement("tr");
                                    htmlWriter.startElement("td").characters("Task Execution").endElement();
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(model.getElapsedTotalExecutionTime())).endElement();
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
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(profiledProjectConfiguration.getElapsedTime())).endElement();
                                htmlWriter.endElement();
                                for (Operation operation : profiledProjectConfiguration) {
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("td").characters(operation.getDescription()).endElement();
                                        htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(operation.getElapsedTime())).endElement();
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
                                    htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(model.getDependencySets().getElapsedTime())).endElement();
                                htmlWriter.endElement();

                                for (Operation operation : model.getDependencySets()) {
                                    htmlWriter.startElement("tr");
                                        htmlWriter.startElement("td").characters(operation.getDescription()).endElement();
                                        htmlWriter.startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(operation.getElapsedTime())).endElement();
                                    htmlWriter.endElement();
                                }
                            htmlWriter.endElement()
                        .endElement();
                        htmlWriter.startElement("div").attribute("class", "tab").attribute("id", "tab3");
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
                                        .startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(project.getElapsedTime())).endElement()
                                        .startElement("td").characters("(total)").endElement()
                                    .endElement();
                                    for (TaskExecution taskExecution : project.getTasks()) {
                                        htmlWriter.startElement("tr")
                                            .startElement("td").attribute("class", "indentPath").characters(taskExecution.getPath()).endElement()
                                            .startElement("td").attribute("class", "numeric").characters(DURATION_FORMAT.format(taskExecution.getElapsedTime())).endElement()
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
