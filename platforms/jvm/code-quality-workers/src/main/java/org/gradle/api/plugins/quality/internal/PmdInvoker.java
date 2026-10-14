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
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.plugins.quality.internal.pmd7.PmdRunSpec;
import org.gradle.api.plugins.quality.internal.pmd7.PmdRunner;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.VersionNumber;
import org.gradle.workers.WorkAction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class PmdInvoker implements WorkAction<PmdActionParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmdInvoker.class);

    private static final List<String> BASIC = ImmutableList.of("basic");
    private static final List<String> JAVA_BASIC = ImmutableList.of("java-basic");
    private static final List<String> ERROR_PRONE = ImmutableList.of("category/java/errorprone.xml");
    private static final VersionNumber NEWEST_PMD_6 = VersionNumber.parse("6.55.0");
    private static final VersionNumber PMD_7 = VersionNumber.parse("7.0.0");
    private static final String PMD_7_RUNNER = "org.gradle.api.plugins.quality.internal.pmd7.Pmd7Runner";

    @Inject
    protected abstract IsolatedAntBuilder getIsolatedAntBuilder();

    @Override
    public void execute() {
        LOGGER.info("Running pmd with toolchain '{}'.", Jvm.current().getJavaHome().getAbsolutePath());

        PmdActionParameters parameters = getParameters();
        FileCollection pmdClasspath = parameters.getPmdClasspath().filter(new FileExistFilter());

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.
        if (parameters.getIncrementalAnalysis().get()) {
            // TODO: Can we get rid of this now that we're running in a worker?
            SystemProperties.getInstance().withSystemProperty("java.class.path", GUtil.asPath(pmdClasspath), (Factory<Void>) () -> {
                runPmd(parameters, pmdClasspath);
                return null;
            });
        } else {
            runPmd(parameters, pmdClasspath);
        }
    }

    @SuppressWarnings("FormatStringShouldUsePlaceholders")
    private void runPmd(PmdActionParameters parameters, FileCollection pmdClasspath) {
        // Without version information, assume the newest version that runs through Ant
        VersionNumber version = parameters.getPmdVersion().map(VersionNumber::parse).getOrElse(NEWEST_PMD_6);

        int maxFailures = parameters.getMaxFailures().get();
        if (maxFailures < 0) {
            throw new GradleException(String.format("Invalid maxFailures %s. Valid range is >= 0.", maxFailures));
        }

        int failureCount = version.compareTo(PMD_7) >= 0
            ? runPmd7(parameters, pmdClasspath)
            : runPmdWithAnt(parameters, version);

        if (failureCount > 0) {
            String message = String.format("%s PMD rule violations were found.", failureCount);
            List<PmdActionParameters.EnabledReport> reports = parameters.getEnabledReports().get();
            if (!reports.isEmpty()) {
                String reportUrl = new ConsoleRenderer().asClickableFileUrl(reports.get(0).getOutputLocation().getAsFile().get());
                message += " See the report at: " + reportUrl;
            }
            if (parameters.getIgnoreFailures().get() || failureCount <= maxFailures) {
                LOGGER.warn(message);
            } else {
                throw new MarkedVerificationException(message);
            }
        }
    }

    private int runPmdWithAnt(PmdActionParameters parameters, VersionNumber version) {
        Map<String, Object> antPmdArgs = new HashMap<>(ImmutableMap.<String, Object>builder()
            .put("failOnRuleViolation", false)
            .put("failuresPropertyName", "pmdFailureCount")
            .put("minimumPriority", parameters.getRulesMinimumPriority().get())
            .build()
        );

        List<String> ruleSets = parameters.getRuleSets().get();
        String htmlFormat = "html";
        if (version.compareTo(VersionNumber.parse("5.0.0")) < 0) {
            // <5.x
            // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
            // http://sourceforge.net/p/pmd/bugs/1004/
            // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
            antPmdArgs.put("targetjdk", parameters.getTargetJdk().get().getName());

            htmlFormat = "betterhtml";

            // fallback to basic on pre 5.0 for backwards compatible
            if (ruleSets.equals(JAVA_BASIC) || ruleSets.equals(ERROR_PRONE)) {
                ruleSets = BASIC;
            }
            if (parameters.getIncrementalAnalysis().get()) {
                PmdInvoker.assertUnsupportedIncrementalAnalysis(version);
            }
        } else if (version.compareTo(VersionNumber.parse("6.0.0")) < 0) {
            // 5.x
            if (ruleSets.equals(ERROR_PRONE)) {
                ruleSets = JAVA_BASIC;
            }
            if (parameters.getIncrementalAnalysis().get()) {
                PmdInvoker.assertUnsupportedIncrementalAnalysis(version);
            }
            antPmdArgs.put("threads", parameters.getThreads().get());
        } else {
            // 6.+
            if (parameters.getIncrementalAnalysis().get()) {
                antPmdArgs.put("cacheLocation", parameters.getIncrementalCacheFile().get().getAsFile());
            } else {
                if (version.compareTo(VersionNumber.parse("6.2.0")) >= 0) {
                    antPmdArgs.put("noCache", true);
                }
            }
            antPmdArgs.put("threads", parameters.getThreads().get());
        }

        for (PmdActionParameters.EnabledReport report : parameters.getEnabledReports().get()) {
            if (report.getName().get().equals("sarif")) {
                assertSarifReportSupportedByPmdVersion(version);
            } else if (report.getName().get().equals("codeClimate")) {
                assertCodeClimateReportSupportedByPmdVersion(version);
            }
        }

        String finalHtmlFormat = htmlFormat;
        List<String> finalRuleSets = ruleSets;
        List<PmdActionParameters.EnabledReport> reports = parameters.getEnabledReports().get();
        AtomicReference<String> failureCount = new AtomicReference<>();
        getIsolatedAntBuilder().withClasspath(parameters.getPmdClasspath()).execute(ant -> {
            ant.taskdef("pmd", "net.sourceforge.pmd.ant.PMDTask");
            ant.createNode("pmd", antPmdArgs, () -> {
                ant.addDirectoryTrees("fileset", ((FileCollectionInternal) parameters.getSource()).getAsDirectoryTrees());
                finalRuleSets.forEach(rule -> ant.createNode("ruleset", rule));
                parameters.getRuleSetConfigFiles().forEach(ruleSetConfig -> ant.createNode("ruleset", ruleSetConfig.getAbsolutePath()));

                FileCollection auxClasspath = parameters.getAuxClasspath().filter(new FileExistFilter());
                if (!auxClasspath.isEmpty()) {
                    ant.addFiles("auxclasspath", auxClasspath);
                }

                reports.forEach(report -> {
                    File file = report.getOutputLocation().getAsFile().get();
                    checkArgument(file.getParentFile().exists(), "Parent directory of report file '%s' does not exist.", file);
                    String type;
                    String name = report.getName().get();
                    switch (name) {
                        case "html":
                            type = finalHtmlFormat;
                            break;
                        case "codeClimate":
                            type = "codeclimate";
                            break;
                        default:
                            type = name;
                            break;
                    }
                    ant.createNode("formatter", ImmutableMap.of("type", type, "toFile", file));
                });

                if (parameters.getConsoleOutput().get()) {
                    String consoleOutputType = "text";
                    if (parameters.getStdOutIsAttachedToTerminal().get()) {
                        consoleOutputType = "textcolor";
                    }
                    ant.setSaveStreams(false);
                    ant.createNode("formatter", ImmutableMap.of("type", consoleOutputType, "toConsole", true));
                }
            });
            failureCount.set((String) ant.getProjectProperties().get("pmdFailureCount"));
        });
        return failureCount.get() == null ? 0 : Integer.parseInt(failureCount.get());
    }

    /**
     * Runs PMD 7 or newer through its Java API, in a class loader that contains PMD, the JDK and the logging APIs only.
     */
    private static int runPmd7(PmdActionParameters parameters, FileCollection pmdClasspath) {
        List<String> ruleSets = new ArrayList<>(parameters.getRuleSets().get());
        parameters.getRuleSetConfigFiles().forEach(ruleSetConfig -> ruleSets.add(ruleSetConfig.getAbsolutePath()));
        if (ruleSets.isEmpty()) {
            // Same failure as the PMD Ant task
            throw new GradleException("No rulesets specified");
        }

        Map<String, File> reports = new LinkedHashMap<>();
        for (PmdActionParameters.EnabledReport report : parameters.getEnabledReports().get()) {
            File file = report.getOutputLocation().getAsFile().get();
            checkArgument(file.getParentFile().exists(), "Parent directory of report file '%s' does not exist.", file);
            String name = report.getName().get();
            reports.put(name.equals("codeClimate") ? "codeclimate" : name, file);
        }

        Charset consoleCharset = consoleCharset();
        String consoleRendererType;
        Writer consoleOutput;
        if (parameters.getConsoleOutput().get()) {
            consoleRendererType = parameters.getStdOutIsAttachedToTerminal().get() ? "textcolor" : "text";
            consoleOutput = new OutputStreamWriter(new NonClosingOutputStream(System.out), consoleCharset);
        } else if (reports.isEmpty()) {
            // Like the PMD Ant task, which then reports to its log at info level
            consoleRendererType = "text";
            consoleOutput = new InfoLoggingWriter();
        } else {
            consoleRendererType = null;
            consoleOutput = new InfoLoggingWriter();
        }

        PmdRunSpec spec = new DefaultPmdRunSpec(
            ruleSets,
            parameters.getRulesMinimumPriority().get(),
            parameters.getThreads().get(),
            parameters.getIncrementalAnalysis().get() ? parameters.getIncrementalCacheFile().get().getAsFile() : null,
            ImmutableList.copyOf(parameters.getAuxClasspath().filter(new FileExistFilter())),
            ImmutableList.copyOf(parameters.getSource()),
            reports,
            consoleRendererType,
            consoleOutput,
            consoleCharset
        );

        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        filterSpec.allowPackage("org.slf4j");
        filterSpec.allowPackage("org.apache.commons.logging");
        filterSpec.allowPackage("org.apache.log4j");
        filterSpec.allowClass(PmdRunner.class);
        filterSpec.allowClass(PmdRunSpec.class);
        FilteringClassLoader parent = new FilteringClassLoader(PmdInvoker.class.getClassLoader(), filterSpec);

        List<URL> classpath = new ArrayList<>();
        for (File file : pmdClasspath) {
            classpath.add(toUrl(file));
        }
        classpath.add(toUrl(ClasspathUtil.getClasspathForClass(PmdRunner.class)));

        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        try (VisitableURLClassLoader pmdLoader = new VisitableURLClassLoader("pmd-loader", parent, classpath)) {
            thread.setContextClassLoader(pmdLoader);
            PmdRunner runner = pmdLoader.loadClass(PMD_7_RUNNER).asSubclass(PmdRunner.class).getConstructor().newInstance();
            return runner.run(spec);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("Could not create the PMD runner.", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
    }

    private static Charset consoleCharset() {
        // Matches the PMD Ant task, which uses the console's encoding when there is a console and file.encoding otherwise
        String encoding = System.getProperty("stdout.encoding");
        if (encoding == null) {
            encoding = System.getProperty("file.encoding");
        }
        return encoding == null ? Charset.defaultCharset() : Charset.forName(encoding);
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertUnsupportedIncrementalAnalysis(VersionNumber version) {
        throw new GradleException("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD " + version + " or disable incremental analysis.");
    }

    private static void assertSarifReportSupportedByPmdVersion(VersionNumber version) {
        if (version.compareTo(VersionNumber.parse("6.31.0")) < 0) {
            // https://github.com/pmd/pmd/releases/tag/pmd_releases%2F6.31.0
            throw new GradleException("The Sarif output format is only supported by PMD 6.31.0 and newer. Please upgrade from PMD " + version + " or disable the 'sarif' report.");
        }
    }

    private static void assertCodeClimateReportSupportedByPmdVersion(VersionNumber version) {
        if (version.compareTo(VersionNumber.parse("5.3.7")) < 0) {
            // https://github.com/pmd/pmd/releases/tag/pmd_releases%2F5.3.7
            throw new GradleException("The CodeClimate output format is only supported by PMD 5.3.7 and newer. Please upgrade from PMD " + version + " or disable the 'codeClimate' report.");
        }
    }

    private static class DefaultPmdRunSpec implements PmdRunSpec {
        private final List<String> ruleSets;
        private final int minimumPriority;
        private final int threads;
        private final @Nullable File incrementalCacheFile;
        private final List<File> auxClasspath;
        private final List<File> sourceFiles;
        private final Map<String, File> reports;
        private final @Nullable String consoleRendererType;
        private final Writer consoleOutput;
        private final Charset consoleCharset;

        DefaultPmdRunSpec(
            List<String> ruleSets,
            int minimumPriority,
            int threads,
            @Nullable File incrementalCacheFile,
            List<File> auxClasspath,
            List<File> sourceFiles,
            Map<String, File> reports,
            @Nullable String consoleRendererType,
            Writer consoleOutput,
            Charset consoleCharset
        ) {
            this.ruleSets = ruleSets;
            this.minimumPriority = minimumPriority;
            this.threads = threads;
            this.incrementalCacheFile = incrementalCacheFile;
            this.auxClasspath = auxClasspath;
            this.sourceFiles = sourceFiles;
            this.reports = reports;
            this.consoleRendererType = consoleRendererType;
            this.consoleOutput = consoleOutput;
            this.consoleCharset = consoleCharset;
        }

        @Override
        public List<String> getRuleSets() {
            return ruleSets;
        }

        @Override
        public int getMinimumPriority() {
            return minimumPriority;
        }

        @Override
        public int getThreads() {
            return threads;
        }

        @Override
        public @Nullable File getIncrementalCacheFile() {
            return incrementalCacheFile;
        }

        @Override
        public List<File> getAuxClasspath() {
            return auxClasspath;
        }

        @Override
        public List<File> getSourceFiles() {
            return sourceFiles;
        }

        @Override
        public Map<String, File> getReports() {
            return reports;
        }

        @Override
        public @Nullable String getConsoleRendererType() {
            return consoleRendererType;
        }

        @Override
        public Writer getConsoleOutput() {
            return consoleOutput;
        }

        @Override
        public Charset getConsoleCharset() {
            return consoleCharset;
        }
    }

    /**
     * Keeps {@code System.out} open when PMD closes its console writer.
     */
    private static class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    /**
     * Logs each line at info level.
     */
    private static class InfoLoggingWriter extends Writer {
        private final StringBuilder line = new StringBuilder();

        @Override
        public void write(char[] cbuf, int off, int len) {
            for (int i = off; i < off + len; i++) {
                char c = cbuf[i];
                if (c == '\n') {
                    logLine();
                } else if (c != '\r') {
                    line.append(c);
                }
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            if (line.length() > 0) {
                logLine();
            }
        }

        private void logLine() {
            LOGGER.info(line.toString());
            line.setLength(0);
        }
    }

    private static class FileExistFilter implements Spec<File> {
        @Override
        public boolean isSatisfiedBy(File element) {
            return element.exists();
        }
    }
}
