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
import org.gradle.api.Nullable;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.List;

public class PrettyPrefixedLogHeaderFormatter implements LogHeaderFormatter {
    @Override
    public List<StyledTextOutputEvent.Span> format(@Nullable String header, String description, @Nullable String shortDescription, @Nullable String status) {
        final String message = header != null ? header : description;
        if (message != null) {
            // Visually indicate group by adding surrounding lines
            return Lists.newArrayList(new StyledTextOutputEvent.Span(EOL), new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, "> " + message), new StyledTextOutputEvent.Span(EOL));
        } else {
            return Lists.newArrayList();
        }
    }
}
