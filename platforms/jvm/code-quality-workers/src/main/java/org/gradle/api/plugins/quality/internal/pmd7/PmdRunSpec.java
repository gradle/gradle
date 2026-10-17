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

package org.gradle.api.plugins.quality.internal.pmd7;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Configuration of a PMD run.
 * <p>
 * Shared between Gradle and the class loader that runs PMD, so it must only reference JDK types.
 */
public interface PmdRunSpec {

    /**
     * Rule set references and rule set files.
     */
    List<String> getRuleSets();

    int getMinimumPriority();

    int getThreads();

    /**
     * The incremental analysis cache, or {@code null} if incremental analysis is disabled.
     */
    @Nullable
    File getIncrementalCacheFile();

    List<File> getAuxClasspath();

    List<File> getSourceFiles();

    /**
     * PMD renderer type to report file, in report order.
     */
    Map<String, File> getReports();

    /**
     * PMD renderer type for the console output, or {@code null} for no console output.
     */
    @Nullable
    String getConsoleRendererType();

    /**
     * Receives the console output. PMD closes it after the analysis.
     */
    Writer getConsoleOutput();

    Charset getConsoleCharset();
}
