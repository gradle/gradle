/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.text;

import org.gradle.logging.StyledTextOutput;

import static org.gradle.logging.StyledTextOutput.Style.Header;
import static org.gradle.logging.StyledTextOutput.Style.Normal;

public class DefaultTextReportBuilder implements TextReportBuilder {
    public static final String SEPARATOR = "------------------------------------------------------------";
    private final StyledTextOutput textOutput;

    public DefaultTextReportBuilder(StyledTextOutput textOutput) {
        this.textOutput = textOutput;
    }

    public void writeHeading(String heading) {
        textOutput.println().style(Header);
        textOutput.println(SEPARATOR);
        textOutput.println(heading);
        textOutput.text(SEPARATOR);
        textOutput.style(Normal);
        textOutput.println().println();
    }

    public void writeSubheading(String heading) {
        textOutput.style(Header).println(heading);
        for (int i = 0; i < heading.length(); i++) {
            textOutput.text("-");
        }
        textOutput.style(Normal).println();
    }

    public StyledTextOutput getOutput() {
        return textOutput;
    }
}
