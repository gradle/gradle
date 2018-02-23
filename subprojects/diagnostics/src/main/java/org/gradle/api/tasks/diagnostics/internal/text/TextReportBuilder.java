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

import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.reporting.ReportRenderer;

import java.io.File;
import java.util.Collection;

public interface TextReportBuilder {
    /**
     * Outputs the heading for the current item. This should be called before any of the {@code item} methods are called.
     */
    void heading(String heading);

    void subheading(String heading);

    <T> void collection(String title, Collection<? extends T> items, ReportRenderer<T, TextReportBuilder> renderer, String elementsPlural);

    <T> void collection(Iterable<? extends T> items, ReportRenderer<T, TextReportBuilder> renderer);

    <T> void item(T value, ReportRenderer<T, TextReportBuilder> renderer);

    void item(String title, String value);

    void item(String title, Iterable<String> values);

    void item(String title, File value);

    void item(String value);

    void item(File value);

    StyledTextOutput getOutput();
}
