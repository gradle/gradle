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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public abstract class HtmlPageGenerator<T> extends ReportRenderer<T, Writer> {
    protected final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public HtmlPageGenerator() {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    protected void headSection(Html html) {
        html.meta().httpEquiv("Content-Type").content("text/html; charset=utf-8");
        html.style().text("body { font-family: sans-serif; margin: 3em; } h2 { font-size: 14pt; margin-top: 2em; } table { width: 100%; font-size: 10pt; } th { text-align: left; } td { white-space: nowrap } #footer { margin-top: 4em; font-size: 8pt; } .test-execution { font-size: 14pt; padding-top: 1em; }").end();
    }

    protected void footer(Html html) {
        html.div()
                .id("footer")
                .text(String.format("Generated at %s by %s", format.format(new Date()), GradleVersion.current()))
                .end();
    }
}
