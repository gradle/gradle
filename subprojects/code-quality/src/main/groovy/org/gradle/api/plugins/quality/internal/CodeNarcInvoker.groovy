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
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.reporting.SingleFileReport
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.logging.ConsoleRenderer

abstract class CodeNarcInvoker {

    static void invoke(CodeNarc codenarcTask) {
        def logging = codenarcTask.logging
        def codenarcClasspath = codenarcTask.codenarcClasspath
        def antBuilder = codenarcTask.antBuilder
        def classpath = new DefaultClassPath(codenarcClasspath)
        def configFile = codenarcTask.configFile
        def maxPriority1Violations = codenarcTask.maxPriority1Violations
        def maxPriority2Violations = codenarcTask.maxPriority2Violations
        def maxPriority3Violations = codenarcTask.maxPriority3Violations
        def reports = codenarcTask.reports
        def ignoreFailures = codenarcTask.ignoreFailures
        def logger = codenarcTask.logger
        def source = codenarcTask.source

        logging.captureStandardOutput(LogLevel.INFO)
        antBuilder.withClasspath(classpath.asFiles).execute {
            ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask')
            try {
                ant.codenarc(ruleSetFiles: "file:${configFile}", maxPriority1Violations: maxPriority1Violations, maxPriority2Violations: maxPriority2Violations, maxPriority3Violations: maxPriority3Violations) {
                    reports.enabled.each { SingleFileReport r ->
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
                    if (ignoreFailures) {
                        logger.warn(message)
                        return
                    }
                    throw new GradleException(message, e)
                }
                throw e
            }
        }
    }

}
