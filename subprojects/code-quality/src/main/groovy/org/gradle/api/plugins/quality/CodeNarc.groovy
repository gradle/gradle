/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.quality.internal.CodeNarcReportsImpl
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.*
import org.gradle.internal.reflect.Instantiator
import org.gradle.logging.ConsoleRenderer
import org.gradle.util.DeprecationLogger

import javax.inject.Inject

/**
 * Runs CodeNarc against some source files.
 */
class CodeNarc extends SourceTask implements VerificationTask, Reporting<CodeNarcReports> {
    /**
     * The class path containing the CodeNarc library to be used.
     */
    @InputFiles
    FileCollection codenarcClasspath

    /**
     * The CodeNarc configuration file to use.
     */
    @InputFile
    File configFile

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    @Input
    int maxPriority1Violations

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    @Input
    int maxPriority2Violations

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    @Input
    int maxPriority3Violations

    /**
     * The format type of the CodeNarc report.
     *
     * @deprecated Use {@code reports.<report-type>.enabled} instead.
     */
    @Deprecated
    String getReportFormat() {
        DeprecationLogger.nagUserOfReplacedProperty("CodeNarc.reportFormat", "reports.<report-type>.enabled")
        reports.firstEnabled?.name
    }

    /**
     * @deprecated Use {@code reports.<report-type>.enabled} instead.
     */
    @Deprecated
    void setReportFormat(String reportFormat) {
        DeprecationLogger.nagUserOfReplacedProperty("CodeNarc.reportFormat", "reports.<report-type>.enabled")
        reports.each {
            it.enabled == it.name == reportFormat
        }
    }

    /**
     * The file to write the report to.
     *
     * @deprecated Use {@code reports.<report-type>.destination} instead.
     */
    @Deprecated
    File getReportFile() {
        DeprecationLogger.nagUserOfReplacedProperty("CodeNarc.reportFile", "reports.<report-type>.destination")
        reports.firstEnabled?.destination
    }

    /**
     * @deprecated Use {@code reports.<report-type>.destination} instead.
     */
    @Deprecated
    void setReportFile(File reportFile) {
        DeprecationLogger.nagUserOfReplacedProperty("CodeNarc.reportFile", "reports.<report-type>.destination")
        reports.firstEnabled?.destination = reportFile
    }

    @Nested
    private final CodeNarcReportsImpl reports

    private final IsolatedAntBuilder antBuilder

    /**
     * Whether or not the build should break when the verifications performed by this task fail.
     */
    boolean ignoreFailures

    @Inject
    CodeNarc(Instantiator instantiator, IsolatedAntBuilder antBuilder) {
        reports = instantiator.newInstance(CodeNarcReportsImpl, this)
        this.antBuilder = antBuilder
    }

    @TaskAction
    void run() {
        logging.captureStandardOutput(LogLevel.INFO)
        antBuilder.withClasspath(getCodenarcClasspath()).execute {
            ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask')
            try {
                ant.codenarc(ruleSetFiles: "file:${getConfigFile()}", maxPriority1Violations: getMaxPriority1Violations(), maxPriority2Violations: getMaxPriority2Violations(), maxPriority3Violations: getMaxPriority3Violations()) {
                    reports.enabled.each { Report r ->
                        report(type: r.name) {
                            option(name: 'outputFile', value: r.destination)
                        }
                    }

                    source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                }
            } catch (Exception e) {
                if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                    def message = "CodeNarc rule violations were found."
                    def report = reports.firstEnabled
                    if (report) {
                        def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                        message += " See the report at: $reportUrl"
                    }
                    if (getIgnoreFailures()) {
                        logger.warn(message)
                        return
                    }
                    throw new GradleException(message, e)
                }
                throw e
            }
        }
    }

    /**
     * Returns the reports to be generated by this task.
     */
    CodeNarcReports getReports() {
        return reports
    }

    /**
     * Configures the reports to be generated by this task.
     */
    CodeNarcReports reports(Closure closure) {
        reports.configure(closure)
    }
}
