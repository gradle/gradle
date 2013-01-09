/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.reporting.internal;

import com.googlecode.jatl.Html;
import org.gradle.api.reporting.Report;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

public class BuildDashboardGenerator {
    private Set<Report> reports;
    private File outputFile;

    public BuildDashboardGenerator(Set<Report> reports, File outputFile) {
        this.reports = reports;
        this.outputFile = outputFile;
    }

    public void generate() {
        try {
            GFileUtils.parentMkdirs(outputFile);
            FileWriter writer = new FileWriter(outputFile);
            generate(writer);
            writer.close();
            copyCss();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyCss() {
        File cssFile = new File(outputFile.getParent(), "base-style.css");
        GFileUtils.copyURLToFile(getClass().getResource("/org/gradle/reporting/base-style.css"), cssFile);
    }

    private Set<Report> getReportsWithExistingDestination() {
        return CollectionUtils.filter(reports, new Spec<Report>() {
            public boolean isSatisfiedBy(Report report) {
                File destination = report.getDestination();
                return destination != null && destination.exists();
            }
        });
    }

    private void generate(FileWriter writer) {
        new Html(writer) {{
            html();
                head();
                    link().rel("stylesheet").type("text/css").href("base-style.css");
                end(2);
                body();
                div().id("content");
                    Set<Report> reports = getReportsWithExistingDestination();
                    if (reports.size() > 0) {
                        h1().text("Available build reports:").end();
                        ul();
                        for (Report report : reports) {
                            li();
                                a().href(GFileUtils.relativePath(outputFile.getParentFile(), report.getDestination())).text(report.getDisplayName());
                            end(2);
                        }
                        end();
                    } else {
                        h1().text("There are no build reports available.").end();
                    }
            endAll();
        }};
    }
}
