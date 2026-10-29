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

import org.codenarc.CodeNarcRunner;
import org.codenarc.report.ReportWriter;
import org.codenarc.report.ReportWriterFactory;
import org.codenarc.results.Results;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runs CodeNarc through {@link CodeNarcRunner}, mirroring what the CodeNarc Ant task does.
 * <p>
 * Loaded in a class loader that contains only CodeNarc, its Groovy, the JDK and the logging APIs,
 * so this class must not reference any Gradle type except {@link CodeNarcAdapter} and {@link CodeNarcRunSpec}.
 * Runs against all CodeNarc versions, so it must only use CodeNarc API that exists since CodeNarc 0.24.1.
 */
public class DefaultCodeNarcAdapter implements CodeNarcAdapter {

    @Override
    public int[] run(CodeNarcRunSpec spec) {
        CodeNarcRunner runner = new CodeNarcRunner();
        runner.setRuleSetFiles(spec.getRuleSetFiles());
        runner.setSourceAnalyzer(new GradleSourceAnalyzer(spec.getSourceTrees()));
        runner.setReportWriters(createReportWriters(spec));

        Results results = execute(runner, spec.getCompilationClasspath());
        return new int[]{
            results.getNumberOfViolationsWithPriority(1, true),
            results.getNumberOfViolationsWithPriority(2, true),
            results.getNumberOfViolationsWithPriority(3, true)
        };
    }

    private static List<ReportWriter> createReportWriters(CodeNarcRunSpec spec) {
        // Report options are passed as strings, like the Ant task does
        ReportWriterFactory factory = new ReportWriterFactory();
        List<ReportWriter> reportWriters = new ArrayList<>();
        for (Map.Entry<String, File> report : spec.getReportFiles().entrySet()) {
            reportWriters.add(factory.getReportWriter(report.getKey(), Collections.singletonMap("outputFile", report.getValue().getAbsolutePath())));
        }
        if (spec.isConsoleReport()) {
            // The IDE format produces a clickable link to the violation on the console
            reportWriters.add(factory.getReportWriter("ide", Collections.singletonMap("writeToStandardOut", "true")));
        }
        return reportWriters;
    }

    private static Results execute(CodeNarcRunner runner, List<File> compilationClasspath) {
        if (compilationClasspath.isEmpty()) {
            return runner.execute();
        }
        // Like the Ant task, expose the compilation classpath to enhanced rules through the context class loader
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(toUrls(compilationClasspath), previousContextClassLoader)) {
            thread.setContextClassLoader(classLoader);
            return runner.execute();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    private static URL[] toUrls(List<File> files) {
        URL[] urls = new URL[files.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = files.get(i).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return urls;
    }
}
