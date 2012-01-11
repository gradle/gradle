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

import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.*
//import org.gradle.api.plugins.quality.internal.ConsoleReportWriter
import org.gradle.api.file.FileCollection
import org.apache.tools.ant.BuildException
import org.gradle.api.GradleException

/**
 * Runs CodeNarc against some source files.
 */
class CodeNarc extends SourceTask implements VerificationTask {
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
    String reportFormat

    /**
     * The file to write the report to.
     */
    @OutputFile
    File reportFile

    /**
     * Whether or not the build should break when the verifications performed by this task fail.
     */
    Boolean ignoreFailures

    @TaskAction
    void run() {
        logging.captureStandardOutput(LogLevel.INFO)

        ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask', classpath: getCodenarcClasspath().asPath)

        try {
            ant.codenarc(ruleSetFiles: "file:${getConfigFile()}", maxPriority1Violations: 0, maxPriority2Violations: 0, maxPriority3Violations: 0) {
                report(type: getReportFormat()) {
                    option(name: 'outputFile', value: getReportFile())
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
}
