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
import org.gradle.api.plugins.quality.Pmd
import org.gradle.internal.logging.ConsoleRenderer

abstract class PmdInvoker {
    static void invoke(Pmd pmdTask) {
        def pmdClasspath = pmdTask.pmdClasspath
        def targetJdk = pmdTask.targetJdk
        def ruleSets = pmdTask.ruleSets
        def rulePriority = pmdTask.rulePriority
        def antBuilder = pmdTask.antBuilder
        def source = pmdTask.source
        def ruleSetFiles = pmdTask.ruleSetFiles
        def ruleSetConfig = pmdTask.ruleSetConfig
        def classpath = pmdTask.classpath
        def reports = pmdTask.reports
        def consoleOutput = pmdTask.consoleOutput
        def stdOutIsAttachedToTerminal = pmdTask.stdOutIsAttachedToTerminal()
        def ignoreFailures = pmdTask.ignoreFailures
        def logger = pmdTask.logger

        def prePmd5 = pmdClasspath.any {
            it.name ==~ /pmd-([1-4]\.[0-9\.]+)\.jar/
        }
        def antPmdArgs = [failOnRuleViolation: false, failuresPropertyName: "pmdFailureCount"]
        if (prePmd5) {
            // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
            // http://sourceforge.net/p/pmd/bugs/1004/
            // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
            antPmdArgs["targetjdk"] = targetJdk.name

            // fallback to basic on pre 5.0 for backwards compatible
            if (ruleSets == ["java-basic"]) {
                ruleSets = ['basic']
                pmdTask.setRuleSets(ruleSets)
            }
        }

        antPmdArgs["minimumPriority"] = rulePriority

        antBuilder.withClasspath(pmdClasspath).execute { a ->
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

                if (reports.html.enabled) {
                    assert reports.html.destination.parentFile.exists()
                    formatter(type: prePmd5 ? "betterhtml" : "html", toFile: reports.html.destination)
                }
                if (reports.xml.enabled) {
                    formatter(type: 'xml', toFile: reports.xml.destination)
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
                    def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                    message += " See the report at: $reportUrl"
                }
                if (ignoreFailures) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
            }
        }
    }
}
