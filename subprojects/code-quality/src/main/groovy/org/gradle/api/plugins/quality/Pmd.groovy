/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.plugins.quality.internal.PmdReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.*
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.reflect.Instantiator
import org.gradle.logging.ConsoleRenderer

import javax.inject.Inject

/**
 * Runs a set of static code analysis rules on Java source code files and
 * generates a report of problems found.
 *
 * @see PmdPlugin
 */
class Pmd extends SourceTask implements VerificationTask, Reporting<PmdReports> {
    /**
     * The class path containing the PMD library to be used.
     */
    @InputFiles
    FileCollection pmdClasspath

    /**
     * The built-in rule sets to be used. See the <a href="http://pmd.sourceforge.net/rules/index.html">official list</a> of built-in rule sets.
     *
     * Example: ruleSets = ["basic", "braces"]
     */
    @Input
    List<String> ruleSets

    /**
     * The target JDK to use with PMD.
     */
    @Input
    TargetJdk targetJdk

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that
     * it does not currently support multiple rule sets.
     *
     * See the
     * <a href="http://pmd.sourceforge.net/howtomakearuleset.html">official documentation</a>
     * for how to author a rule set.
     *
     * Example: ruleSetConfig = resources.text.fromFile(resources.file("config/pmd/myRuleSets.xml"))
     *
     * @since 2.2
     */
    @Incubating
    @Nested
    @Optional
    TextResource ruleSetConfig

    /**
     * The custom rule set files to be used. See the <a href="http://pmd.sourceforge.net/howtomakearuleset.html">official documentation</a> for
     * how to author a rule set file.
     *
     * Example: ruleSetFiles = files("config/pmd/myRuleSets.xml")
     */
    @InputFiles
    FileCollection ruleSetFiles

    @Nested
    private final PmdReportsImpl reports

    /**
     * Whether or not to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    boolean ignoreFailures

    /**
     * Whether or not to write PMD results to {@code System.out}.
     */
    @Incubating
    boolean consoleOutput

    Pmd() {
        reports = instantiator.newInstance(PmdReportsImpl, this)
    }

    @Inject
    Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void run() {
        def prePmd5 = getPmdClasspath().any {
            it.name ==~ /pmd-([1-4]\.[0-9\.]+)\.jar/
        }
        def antPmdArgs = [failOnRuleViolation: false, failuresPropertyName: "pmdFailureCount"]
        if (prePmd5) {
            // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
            // http://sourceforge.net/p/pmd/bugs/1004/
            // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
            antPmdArgs["targetjdk"] = getTargetJdk().getName()
        } else {
            // allow PmdPlugin to set a version-independent default
            if (getRuleSets() == ["basic"]) {
                setRuleSets(["java-basic"])
            }
        }

        antBuilder.withClasspath(getPmdClasspath()).execute { a ->
            ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask')
            ant.pmd(antPmdArgs) {
                getSource().addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                getRuleSets().each {
                    ruleset(it)
                }
                getRuleSetFiles().each {
                    ruleset(it)
                }
                def ruleSetConfig = getRuleSetConfig()
                if (ruleSetConfig != null) {
                    ruleset(ruleSetConfig.asFile())
                }

                if (reports.html.enabled) {
                    assert reports.html.destination.parentFile.exists()
                    formatter(type: prePmd5 ? "betterhtml" : "html", toFile: reports.html.destination)
                }
                if (reports.xml.enabled) {
                    formatter(type: 'xml', toFile: reports.xml.destination)
                }

                if (getConsoleOutput()) {
                    def consoleOutputType = 'text'
                    if (stdOutIsAttachedToTerminal()) {
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
                if (getIgnoreFailures()) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
            }
        }
    }

    boolean stdOutIsAttachedToTerminal() {
        ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class)
        ConsoleMetaData consoleMetaData = consoleDetector.getConsole()
        consoleMetaData?.stdOut
    }

    /**
     * Configures the reports to be generated by this task.
     */
    PmdReports reports(Closure closure) {
        reports.configure(closure)
    }

    /**
     * Returns the reports to be generated by this task.
     */
    PmdReports getReports() {
        reports
    }
}
