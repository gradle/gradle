/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.googlecode.jatl.Html;
import org.gradle.performance.measure.Amount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.GradleVersion;

import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.gradle.performance.results.FormatSupport.executionTimestamp;
import static org.gradle.performance.results.FormatSupport.getDifferenceRatio;
import static org.gradle.performance.results.FormatSupport.getFormattedConfidence;
import static org.gradle.performance.results.FormatSupport.getFormattedDifference;

public abstract class HtmlPageGenerator<T> extends ReportRenderer<T, Writer> {
    protected int getDepth() {
        return 0;
    }

    protected void metaTag(Html html) {
        html.meta()
            .httpEquiv("Content-Type")
            .content("text/html; charset=utf-8");
    }

    protected void headSection(Html html) {
        String rootDir = getDepth() == 0 ? "" : "../";
        metaTag(html);
        html.link()
            .rel("stylesheet")
            .type("text/css")
            .href(rootDir + "css/style.css")
            .end();
        html.script()
            .src(rootDir + "js/jquery.min-3.5.1.js")
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

    protected static void footer(Html html) {
        html.div()
            .id("footer")
            .text(String.format("Generated at %s by %s", executionTimestamp(), GradleVersion.current()))
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

        private Stream<DataSeries<Duration>> totalTimeStream(Iterable<MeasuredOperationList> experiments) {
            return StreamSupport.stream(experiments.spliterator(), false).map(MeasuredOperationList::getTotalTime);
        }

        private Stream<Amount<Duration>> totalTimeMedianStream(Iterable<MeasuredOperationList> experiments) {
            return totalTimeStream(experiments).filter((DataSeries<Duration> data) -> !data.isEmpty()).map(DataSeries::getMedian);
        }

        protected void renderSamplesForExperiment(Iterable<MeasuredOperationList> experiments) {
            List<DataSeries<Duration>> executionVersions = totalTimeStream(experiments)
                .map((DataSeries<Duration> data) -> data.isEmpty() ? null : data)
                .collect(Collectors.toList());

            Amount<Duration> min = totalTimeMedianStream(experiments).min(Comparator.naturalOrder()).orElse(null);
            Amount<Duration> max = totalTimeMedianStream(experiments).max(Comparator.naturalOrder()).orElse(null);

            if (min != null && min.equals(max)) {
                min = null;
                max = null;
            }

            for (DataSeries<Duration> data : executionVersions) {
                if (data == null) {
                    td().text("").end();
                    td().text("").end();
                } else {
                    Amount<Duration> value = data.getMedian();
                    Amount<Duration> se = data.getStandardError();
                    String classAttr = "numeric";
                    if (value.equals(min)) {
                        classAttr += " min-value";
                    }
                    if (value.equals(max)) {
                        classAttr += " max-value";
                    }
                    td()
                        .classAttr(classAttr)
                        .title("median: " + value + ", min: " + data.getMin() + ", max: " + data.getMax() + ", se: " + se + ", values: " + data)
                        .text(value.format())
                        .end();
                    td()
                        .classAttr("numeric more-detail")
                        .text("se: " + se.format())
                        .end();
                }
            }

            Optional<DataSeries<Duration>> first = executionVersions.stream().filter(Objects::nonNull).findFirst();
            Optional<DataSeries<Duration>> last = Lists.reverse(executionVersions).stream().filter(Objects::nonNull).findFirst();
            if (first.isPresent() && last.isPresent() && first.get() != last.get()) {
                td().classAttr("numeric").text(getFormattedConfidence(first.get(), last.get())).end();

                String differenceCss = getDifferenceRatio(first.get(), last.get()).doubleValue() > 0 ? "max-value" : "min-value";
                td().classAttr("numeric " + differenceCss).text(getFormattedDifference(first.get(), last.get())).end();
            } else {
                td().text("").end();
                td().text("").end();
            }
        }
    }

    protected String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
