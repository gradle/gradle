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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

class PmdInvoker implements Action<AntBuilderDelegate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmdInvoker.class);

    private static final List<String> BASIC = ImmutableList.of("basic");
    private static final List<String> JAVA_BASIC = ImmutableList.of("java-basic");
    private static final List<String> ERROR_PRONE = ImmutableList.of("category/java/errorprone.xml");

    private final PmdActionParameters parameters;

    PmdInvoker(PmdActionParameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void execute(AntBuilderDelegate ant) {
        FileCollection pmdClasspath = parameters.getPmdClasspath().filter(new FileExistFilter());

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.
        if (parameters.getIncrementalAnalysis().get()) {
            // TODO: Can we get rid of this now that we're running in a worker?
            SystemProperties.getInstance().withSystemProperty("java.class.path", GUtil.asPath(pmdClasspath), (Factory<Void>) () -> {
                runPmd(ant, parameters);
                return null;
            });
        } else {
            runPmd(ant, parameters);
        }
    }

    private static void runPmd(AntBuilderDelegate ant, PmdActionParameters parameters) {
        VersionNumber version = determinePmdVersion(Thread.currentThread().getContextClassLoader());

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

        int maxFailures = parameters.getMaxFailures().get();
        if (maxFailures < 0) {
            throw new GradleException(String.format("Invalid maxFailures %s. Valid range is >= 0.", maxFailures));
        }

        String finalHtmlFormat = htmlFormat;
        List<String> finalRuleSets = ruleSets;
        List<PmdActionParameters.EnabledReport> reports = parameters.getEnabledReports().get();
        ant.taskdef(ImmutableMap.of("name", "pmd", "classname", "net.sourceforge.pmd.ant.PMDTask"));
        ant.invokeMethod("pmd", antPmdArgs, () -> {
            parameters.getSource().addToAntBuilder(ant, "fileset", FileCollection.AntType.FileSet);
            finalRuleSets.forEach(rule -> ant.invokeMethod("ruleset", rule));
            parameters.getRuleSetConfigFiles().forEach(ruleSetConfig -> ant.invokeMethod("ruleset", ruleSetConfig));

            FileCollection auxClasspath = parameters.getAuxClasspath().filter(new FileExistFilter());
            if (!auxClasspath.isEmpty()) {
                auxClasspath.addToAntBuilder(ant, "auxclasspath", FileCollection.AntType.ResourceCollection);
            }

            reports.forEach(report -> {
                File file = report.getOutputLocation().getAsFile().get();
                checkArgument(file.getParentFile().exists(), "Parent directory of report file '" + file + "' does not exist.");
                String type = report.getName().get().equals("html") ? finalHtmlFormat : report.getName().get();
                ant.invokeMethod("formatter", ImmutableMap.of("type", type, "toFile", file));
            });

            if (parameters.getConsoleOutput().get()) {
                String consoleOutputType = "text";
                if (parameters.getStdOutIsAttachedToTerminal().get()) {
                    consoleOutputType = "textcolor";
                }
                disableSaveStreams(ant);
                ant.invokeMethod("formatter", ImmutableMap.of("type", consoleOutputType, "toConsole", true));
            }
        });
        String failureCount = (String) ant.getProjectProperties().get("pmdFailureCount");
        if (failureCount != null) {
            String message = String.format("%s PMD rule violations were found.", failureCount);
            PmdActionParameters.EnabledReport report = reports.isEmpty() ? null : reports.get(0);
            if (report != null) {
                String reportUrl = new ConsoleRenderer().asClickableFileUrl(report.getOutputLocation().getAsFile().get());
                message += " See the report at: " + reportUrl;
            }
            if (parameters.getIgnoreFailures().get() || Integer.parseInt(failureCount) <= maxFailures) {
                LOGGER.warn(message);
            } else {
                throw new MarkedVerificationException(message);
            }
        }
    }

    private static void disableSaveStreams(AntBuilderDelegate ant) {
        try {
            ant.getBuilder().getClass().getMethod("setSaveStreams", boolean.class).invoke(ant.getBuilder(), false);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static VersionNumber determinePmdVersion(ClassLoader antLoader) {
        Class<?> pmdVersion;
        try {
            pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMDVersion");
        } catch (ClassNotFoundException e) {
            try {
                pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMD");
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        try {
            Field versionField = pmdVersion.getDeclaredField("VERSION");
            return VersionNumber.parse(Cast.castNullable(String.class, versionField.get(null)));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertUnsupportedIncrementalAnalysis(VersionNumber version) {
        throw new GradleException("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD " + version + " or disable incremental analysis.");
    }

    private static class FileExistFilter implements Spec<File> {
        @Override
        public boolean isSatisfiedBy(File element) {
            return element.exists();
        }
    }
}
