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

package org.gradle.api.plugins.quality.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.internal.codenarc.CodeNarcAdapter;
import org.gradle.api.plugins.quality.internal.codenarc.CodeNarcRunSpec;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.groovyloader.GroovySystemLoaderFactory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.util.internal.VersionNumber;
import org.gradle.workers.WorkAction;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class CodeNarcInvoker implements WorkAction<CodeNarcActionParameters> {

    private static final Logger LOGGER = Logging.getLogger(CodeNarcInvoker.class);
    private static final String CODENARC_ADAPTER = "org.gradle.api.plugins.quality.internal.codenarc.DefaultCodeNarcAdapter";
    private static final VersionNumber MIN_COMPILATION_CLASSPATH_VERSION = VersionNumber.parse("0.27.0");

    @Override
    public void execute() {
        LOGGER.info("Running codenarc with toolchain '{}'.", Jvm.current().getJavaHome().getAbsolutePath());

        CodeNarcActionParameters parameters = getParameters();
        List<CodeNarcActionParameters.EnabledReport> reports = parameters.getEnabledReports().get();
        int[] violations = runCodeNarc(parameters, reports);

        if (violations[0] > parameters.getMaxPriority1Violations().get()
            || violations[1] > parameters.getMaxPriority2Violations().get()
            || violations[2] > parameters.getMaxPriority3Violations().get()) {
            String message = "CodeNarc rule violations were found.";
            File humanReadableReport = findHumanReadableReport(reports);
            if (humanReadableReport != null) {
                message += " See the report at: " + new ConsoleRenderer().asClickableFileUrl(humanReadableReport);
            }
            if (parameters.getIgnoreFailures().get()) {
                LOGGER.warn(message);
                return;
            }
            throw new MarkedVerificationException(message);
        }
    }

    private static int[] runCodeNarc(CodeNarcActionParameters parameters, List<CodeNarcActionParameters.EnabledReport> reports) {
        Map<String, File> reportFiles = new LinkedHashMap<>();
        boolean consoleReport = false;
        for (CodeNarcActionParameters.EnabledReport report : reports) {
            String name = report.getName().get();
            if (name.equals("console")) {
                consoleReport = true;
            } else {
                // See https://codenarc.org/codenarc-text-report-writer.html
                reportFiles.put(name.equals("html") ? "sortable" : name, report.getOutputLocation().getAsFile().get());
            }
        }

        CodeNarcRunSpec spec = new DefaultCodeNarcRunSpec(
            "file:" + parameters.getConfig().get().getAsFile(),
            sourceTrees(parameters),
            ImmutableMap.copyOf(reportFiles),
            consoleReport,
            ImmutableList.copyOf(parameters.getCompilationClasspath())
        );

        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        filterSpec.allowPackage("org.slf4j");
        filterSpec.allowPackage("org.apache.commons.logging");
        filterSpec.allowPackage("org.apache.log4j");
        filterSpec.allowClass(CodeNarcAdapter.class);
        filterSpec.allowClass(CodeNarcRunSpec.class);
        FilteringClassLoader parent = new FilteringClassLoader(CodeNarcInvoker.class.getClassLoader(), filterSpec);

        List<URL> classpath = new ArrayList<>();
        for (File file : parameters.getCodenarcClasspath()) {
            classpath.add(toUrl(file));
        }
        classpath.add(toUrl(ClasspathUtil.getClasspathForClass(CodeNarcAdapter.class)));

        // CodeNarc reports and its summary are written to System.out, which the Ant task logged at info level
        PrintStream previousOut = System.out;
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        try (VisitableURLClassLoader codenarcLoader = new VisitableURLClassLoader("codenarc-loader", parent, classpath);
             PrintStream loggingOut = new PrintStream(new LoggingOutputStream(consoleReport ? LogLevel.LIFECYCLE : LogLevel.INFO), true)) {
            System.setOut(loggingOut);
            thread.setContextClassLoader(codenarcLoader);
            try {
                if (!spec.getCompilationClasspath().isEmpty()) {
                    assertCompilationClasspathSupported(codenarcLoader);
                }
                CodeNarcAdapter adapter = codenarcLoader.loadClass(CODENARC_ADAPTER).asSubclass(CodeNarcAdapter.class).getConstructor().newInstance();
                return adapter.run(spec);
            } finally {
                thread.setContextClassLoader(previousContextClassLoader);
                System.setOut(previousOut);
                new GroovySystemLoaderFactory().forClassLoader(codenarcLoader).shutdown();
            }
        } catch (ReflectiveOperationException e) {
            throw new GradleException("Could not create the CodeNarc adapter.", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertCompilationClasspathSupported(ClassLoader codenarcLoader) throws IOException {
        URL versionResource = codenarcLoader.getResource("codenarc-version.txt");
        if (versionResource == null) {
            return;
        }
        String version;
        try (InputStream inputStream = versionResource.openStream()) {
            version = new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8).trim();
        }
        if (VersionNumber.parse(version).compareTo(MIN_COMPILATION_CLASSPATH_VERSION) < 0) {
            throw new GradleException("The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc " + MIN_COMPILATION_CLASSPATH_VERSION + " or newer.");
        }
    }

    /**
     * Groups the source files by the source directory they are relative to, like Ant file sets.
     */
    private static Map<File, List<String>> sourceTrees(CodeNarcActionParameters parameters) {
        Map<File, List<String>> sourceTrees = new LinkedHashMap<>();
        parameters.getSource().getAsFileTree().visit(details -> {
            if (!details.isDirectory()) {
                sourceTrees.computeIfAbsent(sourceDirectoryOf(details), dir -> new ArrayList<>()).add(details.getRelativePath().getPathString());
            }
        });
        return sourceTrees.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, entry -> ImmutableList.copyOf(entry.getValue())));
    }

    private static File sourceDirectoryOf(FileVisitDetails details) {
        File dir = details.getFile();
        for (int i = 0; i < details.getRelativePath().getSegments().length; i++) {
            dir = dir.getParentFile();
        }
        return dir;
    }

    private static @Nullable File findHumanReadableReport(List<CodeNarcActionParameters.EnabledReport> reports) {
        // Prefer HTML > text > XML and don't include a link if we don't recognize the report format
        Map<String, File> reportFiles = reports.stream()
            .collect(Collectors.toMap(report -> report.getName().get(), report -> report.getOutputLocation().getAsFile().get()));
        for (String name : ImmutableList.of("html", "text", "xml")) {
            File file = reportFiles.get(name);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class DefaultCodeNarcRunSpec implements CodeNarcRunSpec {
        private final String ruleSetFiles;
        private final Map<File, List<String>> sourceTrees;
        private final Map<String, File> reportFiles;
        private final boolean consoleReport;
        private final List<File> compilationClasspath;

        DefaultCodeNarcRunSpec(String ruleSetFiles, Map<File, List<String>> sourceTrees, Map<String, File> reportFiles, boolean consoleReport, List<File> compilationClasspath) {
            this.ruleSetFiles = ruleSetFiles;
            this.sourceTrees = sourceTrees;
            this.reportFiles = reportFiles;
            this.consoleReport = consoleReport;
            this.compilationClasspath = compilationClasspath;
        }

        @Override
        public String getRuleSetFiles() {
            return ruleSetFiles;
        }

        @Override
        public Map<File, List<String>> getSourceTrees() {
            return sourceTrees;
        }

        @Override
        public Map<String, File> getReportFiles() {
            return reportFiles;
        }

        @Override
        public boolean isConsoleReport() {
            return consoleReport;
        }

        @Override
        public List<File> getCompilationClasspath() {
            return compilationClasspath;
        }
    }

    /**
     * Logs each line written to it, decoded like {@code System.out} encodes it.
     */
    private static final class LoggingOutputStream extends OutputStream {
        private final LogLevel level;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        LoggingOutputStream(LogLevel level) {
            this.level = level;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                logLine();
            } else {
                line.write(b);
            }
        }

        @Override
        public synchronized void close() {
            if (line.size() > 0) {
                logLine();
            }
        }

        private void logLine() {
            String text = new String(line.toByteArray(), Charset.defaultCharset());
            line.reset();
            LOGGER.log(level, text.endsWith("\r") ? text.substring(0, text.length() - 1) : text);
        }
    }
}
