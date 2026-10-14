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

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.renderers.Renderer;
import net.sourceforge.pmd.renderers.RendererFactory;
import net.sourceforge.pmd.reporting.ReportStatsListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Runs PMD 7 or newer through its Java API, mirroring what the PMD Ant task does.
 * <p>
 * Loaded in a class loader that contains only PMD, the JDK and the logging APIs,
 * so this class must not reference any Gradle type except {@link PmdRunner} and {@link PmdRunSpec}.
 */
public class Pmd7Runner implements PmdRunner {

    @Override
    public int run(PmdRunSpec spec) {
        PMDConfiguration configuration = getPmdConfiguration(spec);
        if (!spec.getAuxClasspath().isEmpty()) {
            configuration.prependAuxClasspath(spec.getAuxClasspath().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
        }

        ReportStatsListener reportStats = new ReportStatsListener();
        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            for (File sourceFile : spec.getSourceFiles()) {
                pmd.files().addFile(sourceFile.toPath());
            }
            for (Map.Entry<String, File> report : spec.getReports().entrySet()) {
                pmd.addRenderer(createRenderer(report.getKey(), StandardCharsets.UTF_8, fileWriter(report.getValue())));
            }
            String consoleRendererType = spec.getConsoleRendererType();
            if (consoleRendererType != null) {
                pmd.addRenderer(createRenderer(consoleRendererType, spec.getConsoleCharset(), spec.getConsoleOutput()));
            }
            pmd.addListener(reportStats);
            pmd.performAnalysis();
        }
        return reportStats.getResult().getNumViolations();
    }

    private static PMDConfiguration getPmdConfiguration(PmdRunSpec spec) {
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setRuleSets(spec.getRuleSets());
        configuration.setMinimumPriority(RulePriority.valueOf(spec.getMinimumPriority()));
        configuration.setThreads(spec.getThreads());
        File incrementalCacheFile = spec.getIncrementalCacheFile();
        if (incrementalCacheFile != null) {
            configuration.setAnalysisCacheLocation(incrementalCacheFile.getAbsolutePath());
        } else {
            configuration.setIgnoreIncrementalAnalysis(true);
        }
        return configuration;
    }

    private static Renderer createRenderer(String type, Charset charset, Writer writer) {
        Properties properties = new Properties();
        properties.setProperty("encoding", charset.name());
        Renderer renderer = RendererFactory.createRenderer(type, properties);
        // The Ant task hides suppressed violations by default, while renderers show them by default
        renderer.setShowSuppressedViolations(false);
        renderer.setWriter(writer);
        return renderer;
    }

    private static Writer fileWriter(File file) {
        try {
            return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
