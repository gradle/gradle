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

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.specs.Spec
import org.gradle.internal.Cast
import org.gradle.internal.Factory
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.VersionNumber

import java.lang.reflect.Field

abstract class PmdInvoker {
    static void invoke(Pmd pmdTask) {
        def pmdClasspath = pmdTask.pmdClasspath.filter(new FileExistFilter())
        def targetJdk = pmdTask.targetJdk
        def ruleSets = pmdTask.ruleSets
        def rulePriority = pmdTask.rulesMinimumPriority.get()
        def antBuilder = pmdTask.antBuilder
        def source = pmdTask.source
        def ruleSetFiles = pmdTask.ruleSetFiles
        def ruleSetConfig = pmdTask.ruleSetConfig
        def classpath = pmdTask.classpath?.filter(new FileExistFilter())
        def reports = pmdTask.reports
        def consoleOutput = pmdTask.consoleOutput
        def stdOutIsAttachedToTerminal = consoleOutput ? pmdTask.stdOutIsAttachedToTerminal() : false
        def ignoreFailures = pmdTask.ignoreFailures
        def maxFailures = pmdTask.maxFailures.get()
        def logger = pmdTask.logger
        def incrementalAnalysis = pmdTask.incrementalAnalysis.get()
        def incrementalCacheFile = pmdTask.incrementalCacheFile

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.
        if (incrementalAnalysis) {
            SystemProperties.instance.withSystemProperty("java.class.path", pmdClasspath.files.join(File.pathSeparator), new Factory<Void>() {
                @Override
                Void create() {
                    runPmd(antBuilder, pmdClasspath, rulePriority, targetJdk, ruleSets, incrementalAnalysis, incrementalCacheFile, maxFailures, source, ruleSetFiles, ruleSetConfig, classpath, reports, consoleOutput, stdOutIsAttachedToTerminal, ignoreFailures, logger)
                    return null
                }
            })
        } else {
            runPmd(antBuilder, pmdClasspath, rulePriority, targetJdk, ruleSets, incrementalAnalysis, incrementalCacheFile, maxFailures, source, ruleSetFiles, ruleSetConfig, classpath, reports, consoleOutput, stdOutIsAttachedToTerminal, ignoreFailures, logger)
        }
    }

    private static runPmd(IsolatedAntBuilder antBuilder, FileCollection pmdClasspath, rulePriority, targetJdk, ruleSets, incrementalAnalysis, incrementalCacheFile, maxFailures, source, ruleSetFiles, ruleSetConfig, classpath, reports, consoleOutput, stdOutIsAttachedToTerminal, ignoreFailures, logger) {
        antBuilder.withClasspath(pmdClasspath).execute { a ->
            VersionNumber version = determinePmdVersion(Thread.currentThread().getContextClassLoader())

            def antPmdArgs = [
                    failOnRuleViolation: false,
                    failuresPropertyName: "pmdFailureCount",
                    minimumPriority: rulePriority,
            ]

            String htmlFormat = "html"
            if (version < VersionNumber.parse("5.0.0")) {
                // <5.x
                // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
                // http://sourceforge.net/p/pmd/bugs/1004/
                // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
                antPmdArgs["targetjdk"] = targetJdk.name

                htmlFormat = "betterhtml"

                // fallback to basic on pre 5.0 for backwards compatible
                if (ruleSets == ["java-basic"] || ruleSets == ["category/java/errorprone.xml"]) {
                    ruleSets = ['basic']
                }
                if (incrementalAnalysis) {
                    assertUnsupportedIncrementalAnalysis(version)
                }
            } else if (version < VersionNumber.parse("6.0.0")) {
                // 5.x
                if (ruleSets == ["category/java/errorprone.xml"]) {
                    ruleSets = ['java-basic']
                }
                if (incrementalAnalysis) {
                    assertUnsupportedIncrementalAnalysis(version)
                }
            } else {
                // 6.+
                if (incrementalAnalysis) {
                    antPmdArgs["cacheLocation"] = incrementalCacheFile
                } else {
                    if (version >= VersionNumber.parse("6.2.0")) {
                        antPmdArgs['noCache'] = true
                    }
                }
            }

            if (maxFailures < 0) {
                throw new GradleException("Invalid maxFailures $maxFailures. Valid range is >= 0.")
            }

            ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask')
            ant.pmd(antPmdArgs) {
                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                ruleSets.each {
                    ruleset(it)
                }
                ruleSetFiles.each {
                    ruleset(it)
                }
                if (ruleSetConfig != null) {
                    ruleset(ruleSetConfig.asFile())
                }

                if (classpath != null) {
                    classpath.addToAntBuilder(ant, 'auxclasspath', FileCollection.AntType.ResourceCollection)
                }

                if (reports.html.required.get()) {
                    assert reports.html.outputLocation.asFile.get().parentFile.exists()
                    formatter(type: htmlFormat, toFile: reports.html.outputLocation.asFile.get())
                }
                if (reports.xml.required.get()) {
                    formatter(type: 'xml', toFile: reports.xml.outputLocation.asFile.get())
                }

                if (consoleOutput) {
                    def consoleOutputType = 'text'
                    if (stdOutIsAttachedToTerminal) {
                        consoleOutputType = 'textcolor'
                    }
                    a.builder.saveStreams = false
                    formatter(type: consoleOutputType, toConsole: true)
                }
            }
            def failureCount = ant.project.properties["pmdFailureCount"]
            if (failureCount) {
                def message = "$failureCount PMD rule violations were found."
                def report = reports.firstEnabled
                if (report) {
                    def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.outputLocation.asFile.get())
                    message += " See the report at: $reportUrl"
                }
                if (ignoreFailures || ((failureCount as Integer) <= maxFailures)) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
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
