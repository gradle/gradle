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

import org.gradle.reporting.DomReportRenderer;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.TabbedPageRenderer;
import org.gradle.reporting.TextDomReportRenderer;
import org.w3c.dom.Element;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class ProfileReportRenderer {
    public void writeTo(BuildProfile buildProfile, File file) {
        HtmlReportRenderer renderer = new HtmlReportRenderer();
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/base-style.css"));
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/report.js"));
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/css3-pie-1.0beta3.htc"));
        renderer.requireResource(getClass().getResource("style.css"));
        renderer.renderer(new ProfilePageRenderer()).writeTo(buildProfile, file);
    }

    private static class ProfilePageRenderer extends TabbedPageRenderer<BuildProfile> {
        static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");

        @Override
        protected String getTitle() {
            return "Profile report";
        }

        @Override
        protected DomReportRenderer<BuildProfile> getHeaderRenderer() {
            return new DomReportRenderer<BuildProfile>() {
                @Override
                public void render(BuildProfile model, Element parent) {
                    Element header = appendWithId(parent, "div", "header");
                    appendWithText(header, "p", String.format("Profiled with tasks: %s", model.getTaskDescription()));
                    appendWithText(header, "p", String.format("Run on: %s", DATE_FORMAT.format(model.getBuildStarted())));
                }
            };
        }

        @Override
        protected DomReportRenderer<BuildProfile> getContentRenderer() {
            return new TextDomReportRenderer<BuildProfile>(new HTMLProfileReport());
        }
    }
}
