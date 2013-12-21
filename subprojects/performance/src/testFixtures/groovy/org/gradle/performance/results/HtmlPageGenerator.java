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

import com.googlecode.jatl.Html;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.GradleVersion;

import java.io.Writer;

public abstract class HtmlPageGenerator<T> extends ReportRenderer<T, Writer> {
    protected final FormatSupport format = new FormatSupport();

    protected void headSection(Html html) {
        html.meta()
                .httpEquiv("Content-Type")
                .content("text/html; charset=utf-8");
        html.link()
                .rel("stylesheet")
                .type("text/css")
                .href("css/style.css")
                .end();
        html.script()
                .src("js/jquery.min-1.8.0.js")
                .end();
        html.script()
                .src("js/flot-0.8.1-min.js")
                .end();
    }

    protected void footer(Html html) {
        html.div()
                .id("footer")
                .text(String.format("Generated at %s by %s", format.executionTimestamp(), GradleVersion.current()))
                .end();
    }
}
