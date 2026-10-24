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

package org.gradle.api.plugins.quality.internal.codenarc;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * The inputs of a CodeNarc run.
 * <p>
 * Shared between Gradle and the class loader that runs CodeNarc, so it must only reference JDK types.
 */
public interface CodeNarcRunSpec {

    /**
     * Comma-separated rule set paths, as accepted by CodeNarc.
     */
    String getRuleSetFiles();

    /**
     * Source files to analyze, as relative paths per source directory, in analysis order.
     */
    Map<File, List<String>> getSourceTrees();

    /**
     * CodeNarc report writer type to report file, in report order.
     */
    Map<String, File> getReportFiles();

    /**
     * Whether to write a report to standard out.
     */
    boolean isConsoleReport();

    /**
     * The classpath used to compile the analyzed sources, used by enhanced rules.
     */
    List<File> getCompilationClasspath();
}
