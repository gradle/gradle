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

import java.util.List;

public interface LogHeaderFormatter {
    /**
     * Given a message, return possibly-styled output for displaying message meant to categorize
     * other messages "below" it, if any.
     */
    List<StyledTextOutputEvent.Span> format(String description, String status, boolean failed);
}
