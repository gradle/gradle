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
package org.gradle.internal.logging.format;

import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.Arrays;
import java.util.List;

import static org.gradle.internal.logging.events.StyledTextOutputEvent.EOL;

public class PrettyPrefixedLogHeaderFormatter implements LogHeaderFormatter {
    @Override
    public List<StyledTextOutputEvent.Span> format(String description, String status, boolean failed) {
        if (status.isEmpty()) {
            return Arrays.asList(header(description, failed), EOL);
        } else {
            return Arrays.asList(header(description, failed), status(status, failed), EOL);
        }
    }

    private StyledTextOutputEvent.Span header(String message, boolean failed) {
        StyledTextOutput.Style messageStyle = failed ? StyledTextOutput.Style.FailureHeader : StyledTextOutput.Style.Header;
        return new StyledTextOutputEvent.Span(messageStyle, "> " + message);
    }

    private StyledTextOutputEvent.Span status(String status, boolean failed) {
        StyledTextOutput.Style statusStyle = failed ? StyledTextOutput.Style.Failure : StyledTextOutput.Style.Info;
        return new StyledTextOutputEvent.Span(statusStyle, " " + status);
    }
}
