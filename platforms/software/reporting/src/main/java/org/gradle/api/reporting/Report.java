/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.reporting;

import org.gradle.api.Namer;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.util.Configurable;

/**
 * A file based report to be created.
 * <p>
 * Tasks that produce reports expose instances of this type for configuration via the {@link Reporting} interface.
 */
public interface Report extends Configurable<Report> {

    Namer<Report> NAMER = new Namer<Report>() {
        @Override
        public String determineName(Report report) {
            return report.getName();
        }
    };

    /**
     * The symbolic name of this report.
     * <p>
     * The name of the report usually indicates the format (e.g. XML, HTML etc.) but can be anything.
     * <p>
     * When part of a {@link ReportContainer}, reports are accessed via their name. That is, given a report container variable
     * named {@code reports} containing a report who's {@code getName()} returns {@code "html"}, the report could be accessed
     * via:
     * <pre>
     * reports.html
     * </pre>
     *
     * @return The name of this report.
     */
    @Input
    String getName();

    /**
     * A more descriptive name of this report. Used when the report is referenced for end users.
     *
     * @return A more descriptive name of this report.
     */
    @Input
    String getDisplayName();

    /**
     * A flag that determines whether this report should be generated or not.
     *
     * @since 6.1
     */
    @Input
    Property<Boolean> getRequired();

    /**
     * The location on the filesystem to generate the report to.
     *
     * @since 6.1
     */
    @Internal("Implementations need to add the correct annotation, @OutputDirectory or @OutputFile")
    Property<? extends FileSystemLocation> getOutputLocation();

    /**
     * The type of output the report produces
     */
    enum OutputType {

        /**
         * The report outputs a single file.
         * <p>
         * That is, the {@link #getOutputLocation()} points to a single file.
         */
        FILE,

        /**
         * The report outputs files into a directory.
         * <p>
         * That is, the {@link #getOutputLocation()} points to a directory.
         */
        DIRECTORY
    }

    /**
     * The type of output that the report generates.
     *
     * @return The type of output that the report generates.
     */
    @Input
    OutputType getOutputType();

}
