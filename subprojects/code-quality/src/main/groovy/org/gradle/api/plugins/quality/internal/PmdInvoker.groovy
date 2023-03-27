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

package org.gradle.api.plugins.quality.internal

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.exceptions.MarkedVerificationException
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.specs.Spec
import org.gradle.internal.Cast
import org.gradle.internal.Factory
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Field

class PmdInvoker implements Action<AntBuilderDelegate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmdInvoker.class)

    private final PmdActionParameters parameters

    PmdInvoker(PmdActionParameters parameters) {
        this.parameters = parameters
    }

    void execute(AntBuilderDelegate ant) {
        FileCollection pmdClasspath = parameters.getPmdClasspath().filter(new FileExistFilter())

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.
        if (parameters.incrementalAnalysis.get()) {
            // TODO: Can we get rid of this now that we're running in a worker?
            SystemProperties.instance.withSystemProperty("java.class.path", pmdClasspath.files.join(File.pathSeparator), new Factory<Void>() {
                @Override
                Void create() {
                    runPmd(ant, parameters)
                    return null
                }
            })
        } else {
            runPmd(ant, parameters)
        }
    }

    private static runPmd(AntBuilderDelegate ant, PmdActionParameters parameters) {
        VersionNumber version = determinePmdVersion(Thread.currentThread().getContextClassLoader())

        def antPmdArgs = [
            failOnRuleViolation: false,
            failuresPropertyName: "pmdFailureCount",
            minimumPriority: parameters.rulesMinimumPriority.get(),
        ]

        List<String> ruleSets = parameters.ruleSets.get()
        String htmlFormat = "html"
        if (version < VersionNumber.parse("5.0.0")) {
            // <5.x
            // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
            // http://sourceforge.net/p/pmd/bugs/1004/
            // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
            antPmdArgs["targetjdk"] = parameters.targetJdk.get().name

            htmlFormat = "betterhtml"

            // fallback to basic on pre 5.0 for backwards compatible
            if (ruleSets == ["java-basic"] || ruleSets == ["category/java/errorprone.xml"]) {
                ruleSets = ['basic']
            }
            if (parameters.incrementalAnalysis.get()) {
                PmdInvoker.assertUnsupportedIncrementalAnalysis(version)
            }
        } else if (version < VersionNumber.parse("6.0.0")) {
            // 5.x
            if (ruleSets == ["category/java/errorprone.xml"]) {
                ruleSets = ['java-basic']
            }
            if (parameters.incrementalAnalysis.get()) {
                PmdInvoker.assertUnsupportedIncrementalAnalysis(version)
            }
            antPmdArgs['threads'] = parameters.threads.get()
        } else {
            // 6.+
            if (parameters.incrementalAnalysis.get()) {
                antPmdArgs["cacheLocation"] = parameters.incrementalCacheFile.get().asFile
            } else {
                if (version >= VersionNumber.parse("6.2.0")) {
                    antPmdArgs['noCache'] = true
                }
            }
            antPmdArgs['threads'] = parameters.threads.get()
        }

        int maxFailures = parameters.maxFailures.get()
        if (maxFailures < 0) {
            throw new GradleException("Invalid maxFailures $maxFailures. Valid range is >= 0.")
        }

        List<PmdActionParameters.EnabledReport> reports = parameters.enabledReports.get()
        ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask')
        ant.pmd(antPmdArgs) {
            parameters.source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            ruleSets.each {
                ruleset(it)
            }
            parameters.ruleSetConfigFiles.each {
                ruleset(it)
            }

            FileCollection auxClasspath = parameters.auxClasspath.filter(new FileExistFilter())
            if (!auxClasspath.isEmpty()) {
                auxClasspath.addToAntBuilder(ant, 'auxclasspath', FileCollection.AntType.ResourceCollection)
            }

            reports.each { report ->
                File file = report.outputLocation.asFile.get()
                assert file.parentFile.exists()
                String type = report.name.get() == "html" ? htmlFormat : report.name.get()
                formatter(type: type, toFile: file)
            }

            if (parameters.consoleOutput.get()) {
                def consoleOutputType = 'text'
                if (parameters.stdOutIsAttachedToTerminal.get()) {
                    consoleOutputType = 'textcolor'
                }
                ant.builder.saveStreams = false
                formatter(type: consoleOutputType, toConsole: true)
            }
        }
        def failureCount = ant.builder.project.properties["pmdFailureCount"]
        if (failureCount) {
            def message = "$failureCount PMD rule violations were found."
            def report = reports.isEmpty() ? null : reports.get(0)
            if (report) {
                def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.outputLocation.asFile.get())
                message += " See the report at: $reportUrl"
            }
            if (parameters.ignoreFailures.get() || ((failureCount as Integer) <= maxFailures)) {
                LOGGER.warn(message)
            } else {
                throw new MarkedVerificationException(message)
            }
        }
    }

    private static VersionNumber determinePmdVersion(ClassLoader antLoader) {
        Class pmdVersion
        try {
            pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMDVersion")
        } catch (ClassNotFoundException e) {
            pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMD")
        }
        Field versionField = pmdVersion.getDeclaredField("VERSION")
        return VersionNumber.parse(Cast.castNullable(String.class, versionField.get(null)))
    }

    private static void assertUnsupportedIncrementalAnalysis(VersionNumber version) {
        throw new GradleException("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD " + version + " or disable incremental analysis.")
    }

    private static class FileExistFilter implements Spec<File> {
        @Override
        boolean isSatisfiedBy(File element) {
            return element.exists()
        }
    }
}
