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

package org.gradle.performance.results;

import com.google.common.base.Joiner;
import com.googlecode.jatl.Html;
import org.gradle.api.Transformer;
import org.gradle.performance.measure.Amount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.GradleVersion;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class HtmlPageGenerator<T> extends ReportRenderer<T, Writer> {
    protected final FormatSupport format = new FormatSupport();

    protected int getDepth() {
        return 0;
    }

    protected void headSection(Html html) {
        String rootDir = getDepth() == 0 ? "" : "../";
        html.meta()
            .httpEquiv("Content-Type")
            .content("text/html; charset=utf-8");
        html.link()
            .rel("stylesheet")
            .type("text/css")
            .href(rootDir + "css/style.css")
            .end();
        html.script()
            .src(rootDir + "js/jquery.min-1.11.0.js")
            .end();
        html.script()
            .src(rootDir + "js/flot-0.8.1-min.js")
            .end();
        html.script()
            .src(rootDir + "js/flot.selection.min.js")
            .end();
        html.script()
            .src(rootDir + "js/report.js")
            .end();
        html.script()
            .src(rootDir + "js/performanceGraph.js")
            .end();
    }

    protected void footer(Html html) {
        html.div()
            .id("footer")
            .text(String.format("Generated at %s by %s", format.executionTimestamp(), GradleVersion.current()))
            .end();
    }

    public static class NavigationItem {
        private final String text;
        private final String link;

        public NavigationItem(String text, String link) {
            this.text = text;
            this.link = link;
        }

        public String getLink() {
            return link;
        }

        public String getText() {
            return text;
        }
    }

    protected static class MetricsHtml extends Html {
        public MetricsHtml(Writer writer) {
            super(writer);
        }

        protected void textCell(Object obj) {
            td();
            if (obj != null) {
                text(obj.toString());
            }
            end();
        }

        protected void textCell(Boolean obj) {
            td();
            if (obj != null) {
                text(obj ? "yes" : "no");
            }
            end();
        }

        protected void textCell(Collection<?> obj) {
            td();
            if (obj != null) {
                if (obj.isEmpty()) {
                    span().classAttr("empty").text("-").end();
                } else {
                    text(Joiner.on(" ").join(obj));
                }
            }
            end();
        }

        protected Html nav() {
            return start("nav");
        }


        protected void navigation(List<NavigationItem> navigationItems) {
            nav().id("navigation");
            ul();
            for (NavigationItem navigationItem : navigationItems) {
                li().a().href(navigationItem.getLink()).text(navigationItem.getText()).end().end();
            }
            end();
            end();
        }

        protected int getColumnsForSamples() {
            return 2;
        }

        protected void renderHeaderForSamples(String label) {
            th().colspan(String.valueOf(getColumnsForSamples())).text(label).end();
        }

        protected <T> void renderSamplesForExperiment(Iterable<MeasuredOperationList> experiments, Transformer<DataSeries<T>, MeasuredOperationList> transformer) {
            List<DataSeries<T>> values = new ArrayList<DataSeries<T>>();
            Amount<T> min = null;
            Amount<T> max = null;
            for (MeasuredOperationList testExecution : experiments) {
                DataSeries<T> data = transformer.transform(testExecution);
                if (data.isEmpty()) {
                    values.add(null);
                } else {
                    Amount<T> value = data.getMedian();
                    values.add(data);
                    if (min == null || value.compareTo(min) < 0) {
                        min = value;
                    }
                    if (max == null || value.compareTo(max) > 0) {
                        max = value;
                    }
                }
            }
            if (min != null && min.equals(max)) {
                min = null;
                max = null;
            }

            for (DataSeries<T> data : values) {
                if (data == null) {
                    td().text("").end();
                    td().text("").end();
                } else {
                    Amount<T> value = data.getMedian();
                    Amount<T> stddev = data.getStandardError();
                    String classAttr = "numeric";
                    if (value.equals(min)) {
                        classAttr += " min-value";
                    }
                    if (value.equals(max)) {
                        classAttr += " max-value";
                    }
                    td()
                        .classAttr(classAttr)
                        .title("median: " + value + ", min: " + data.getMin() + ", max: " + data.getMax() + ", stddev: " + stddev + ", values: " + data)
                        .text(value.format())
                        .end();
                    td()
                        .classAttr("numeric more-detail")
                        .text("s: " + stddev.format())
                        .end();
                }
            }
        }
    }
}
