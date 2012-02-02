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

//import org.gradle.api.plugins.quality.internal.ConsoleReportWriter


import org.apache.tools.ant.BuildException
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.quality.internal.CodeNarcReportsImpl
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.Reporting
import org.gradle.api.reporting.internal.ReportContainerBuilder
import org.gradle.api.reporting.internal.TaskGeneratedReport
import org.gradle.api.tasks.*

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
     * The format type of the CodeNarc report.
     */
    @Input
    String getReportFormat() {
        reports.firstEnabled?.name
    }

    void setReportFormat(String reportFormat) {
        reports.each {
            it.enabled == it.name == reportFormat
        }
    }

    /**
     * The file to write the report to
     */
    @OutputFile
    File getReportFile() {
        reports.firstEnabled?.destination
    }

    void setReportFile(File reportFile) {
        reports.firstEnabled?.destination = reportFile
    }

    private final CodeNarcReportsImpl reports = ReportContainerBuilder.forTask(CodeNarcReportsImpl, TaskGeneratedReport, this).build() {
        singleFile "xml"
        singleFile "html"
        singleFile "console"
        singleFile "text"
    }

    /**
     * Whether or not the build should break when the verifications performed by this task fail.
     */
    boolean ignoreFailures

    @TaskAction
    void run() {
        logging.captureStandardOutput(LogLevel.INFO)

        ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask', classpath: getCodenarcClasspath().asPath)

        try {
            ant.codenarc(ruleSetFiles: "file:${getConfigFile()}", maxPriority1Violations: 0, maxPriority2Violations: 0, maxPriority3Violations: 0) {
                if (reportFormat) {
                    report(type: reportFormat) {
                        option(name: 'outputFile', value: reportFile)
                    }
                }

                reports.enabled.each { Report r ->
                    report(type: r.name) {
                        option(name: 'outputFile', value: r.destination)
                    }    
                }

                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            }
        } catch (BuildException e) {
            if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                if (getIgnoreFailures()) {
                    return
                }
                throw new GradleException("CodeNarc rule violations were found. See the report at ${getReportFile()}.", e)
            }
            throw e
        }
    }

    CodeNarcReports getReports() {
        return reports;
    }

    CodeNarcReports reports(Closure closure) {
        reports.configure(closure);
    }


}
