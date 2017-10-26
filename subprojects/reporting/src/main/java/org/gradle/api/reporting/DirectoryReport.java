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

package org.gradle.api.reporting;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;

import java.io.File;

/**
 * A directory based report to be created.
 */
@Incubating
public interface DirectoryReport extends ConfigurableReport {

    /**
     * Returns the entry point of a directory based Report
     *
     * This can be the index.html file in a HTML report
     *
     * @return the entry point of the report or
     * {@link org.gradle.api.reporting.DirectoryReport#getDestination()}
     * if no entry point defined
     *
     */
    @Internal
    File getEntryPoint();

    @Override
    File getDestination();

    /**
     * Always returns {@link Report.OutputType#DIRECTORY}
     *
     * @return {@link Report.OutputType#DIRECTORY}
     */
    @Override
    OutputType getOutputType();
}
