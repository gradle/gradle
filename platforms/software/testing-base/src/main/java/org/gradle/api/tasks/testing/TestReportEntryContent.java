/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.testing.report.generic.EmptyTestReportEntryContent;
import org.gradle.api.internal.tasks.testing.report.generic.HtmlTestReportEntryContent;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;

/**
 * The content of a custom entry in the generic HTML test report, returned by a {@link TestReportEntryContributor}.
 *
 * <p>
 * Instances are created with the static factory methods of this class. It is not meant to be subclassed.
 * </p>
 *
 * <p>
 * More kinds of content may be added in the future.
 * </p>
 *
 * @since 9.10.0
 */
@Incubating
@NullMarked
public abstract class TestReportEntryContent {

    /**
     * Content that is empty, and prevents the entry from being added to the report.
     *
     * @return the content
     * @since 9.10.0
     */
    public static TestReportEntryContent empty() {
        return EmptyTestReportEntryContent.INSTANCE;
    }

    /**
     * Content written as raw HTML.
     *
     * <p>
     * The HTML is written directly into the report, with some restrictions to try and keep it contained within the
     * entry's area. The HTML must not contain {@code <template>} tags or end inside a comment. It is otherwise not
     * validated or sanitized, so this is not a security boundary. For example, {@code <script>} can still affect the
     * rest of the page. It is recommended to respect the boundary, as it's not guaranteed that the structure of the
     * page is stable.
     * </p>
     *
     * @param writer writes the HTML of the entry when the report page is written
     * @return the content
     * @since 9.10.0
     */
    public static TestReportEntryContent html(HtmlWriter writer) {
        return new HtmlTestReportEntryContent(writer);
    }

    /**
     * Writes the HTML of a custom entry.
     *
     * @since 9.10.0
     */
    @Incubating
    @FunctionalInterface
    public interface HtmlWriter {
        /**
         * Writes the HTML of the entry.
         *
         * @param htmlOut where to write the HTML for the entry
         * @throws IOException if writing to {@code htmlOut} fails
         * @since 9.10.0
         */
        void writeHtml(Appendable htmlOut) throws IOException;
    }
}
