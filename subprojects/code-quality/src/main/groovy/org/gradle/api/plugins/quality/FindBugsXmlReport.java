/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.quality;

import org.gradle.api.Incubating;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.Internal;

/**
 * The single file XML report for FindBugs.
 */
@Incubating
public interface FindBugsXmlReport extends SingleFileReport {
    /**
     * Whether or not FindBugs should generate XML augmented with human-readable messages.
     * You should use this format if you plan to generate a report using an XSL stylesheet.
     * <p>
     * If {@code true}, FindBugs will augment the XML with human-readable messages.
     * If {@code false}, FindBugs will not augment the XML with human-readable messages.
     *
     * @return Whether or not FindBugs should generate XML augmented with human-readable messages.
     */
    @Internal
    boolean isWithMessages();

    /**
     * Whether or not FindBugs should generate XML augmented with human-readable messages.
     *
     * @see #isWithMessages()
     * @param withMessages Whether or not FindBugs should generate XML augmented with human-readable messages.
     */
    void setWithMessages(boolean withMessages);

}
