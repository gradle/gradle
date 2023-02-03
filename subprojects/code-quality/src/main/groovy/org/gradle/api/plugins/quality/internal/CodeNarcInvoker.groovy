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
import org.gradle.api.internal.project.ant.AntLoggingAdapter
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
        def classpath = DefaultClassPath.of(codenarcClasspath)
        def compilationClasspath = codenarcTask.compilationClasspath
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
                        // See http://codenarc.sourceforge.net/codenarc-TextReportWriter.html
                        if (r.name == 'console') {
                            setLifecycleLogLevel(ant, 'INFO')
                            report(type: 'text') {
                                option(name: 'writeToStandardOut', value: true)
                            }
                        } else {
                            setLifecycleLogLevel(ant, null)
                            report(type: r.name) {
                                option(name: 'outputFile', value: r.outputLocation.asFile.get())
                            }
                        }
                    }

                    source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)

                    if (!compilationClasspath.empty) {
                        compilationClasspath.addToAntBuilder(ant, 'classpath')
                    }
                }
            } catch (Exception e) {
                if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                    def message = "CodeNarc rule violations were found."
                    def report = reports.firstEnabled
                    if (report && report.name != 'console') {
                        def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.outputLocation.asFile.get())
                        message += " See the report at: $reportUrl"
                    }
                    if (ignoreFailures) {
                        logger.warn(message)
                        return
                    }
                    throw new GradleException(message, e)
                }
                if (e.message == /codenarc doesn't support the nested "classpath" element./) {
                    def message = "The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc 0.27.0 or newer."
                    throw new GradleException(message, e)
                }
                throw e
            }
        }
    }

    static void setLifecycleLogLevel(Object ant, String lifecycleLogLevel) {
        ant?.builder?.project?.buildListeners?.each {
            // We cannot use instanceof or getClass()==AntLoggingAdapter since they're in different class loaders
            if (it.class.name == AntLoggingAdapter.name) {
                it.lifecycleLogLevel = lifecycleLogLevel
            }
        }
    }
}
