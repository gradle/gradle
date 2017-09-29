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

import com.google.common.collect.Lists;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class PrettyPrefixedLogHeaderFormatter implements LogHeaderFormatter {
    @Override
    public List<StyledTextOutputEvent.Span> format(@Nullable String header, String description, @Nullable String shortDescription, @Nullable String status, boolean failed) {
        final String message = header != null ? header : description;
        if (message != null) {
            // Visually indicate group by adding surrounding lines
            return Lists.newArrayList(eol(), header(message, failed), status(status, failed), eol());
        } else {
            return Collections.emptyList();
        }
    }

    private StyledTextOutputEvent.Span eol() {
        return new StyledTextOutputEvent.Span(EOL);
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
