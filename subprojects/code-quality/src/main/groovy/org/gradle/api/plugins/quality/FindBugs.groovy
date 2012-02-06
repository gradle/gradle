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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.Instantiator
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.plugins.quality.internal.FindBugsReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.*

/**
 * Analyzes code with <a href="http://findbugs.sourceforge.net">FindBugs</a>.
 */
class FindBugs extends SourceTask implements VerificationTask, Reporting<FindBugsReports> {
    /**
     * The classes to be analyzed.
     */
    @SkipWhenEmpty
    @InputFiles
    FileCollection classes

    /**
     * Compile class path for the classes to be analyzed.
     * The classes on this class path are used during analysis
     * but aren't analyzed themselves.
     */
    @InputFiles
    FileCollection classpath

    /**
     * Class path holding the FindBugs library.
     */
    @InputFiles
    FileCollection findbugsClasspath

    /**
     * Class path holding any additional FindBugs plugins.
     */
    @InputFiles
    FileCollection pluginClasspath

    /**
     * Whether or not to allow the build to continue if there are warnings.
     */
    boolean ignoreFailures

    @Nested
    private final FindBugsReportsImpl reports = services.get(Instantiator).newInstance(FindBugsReportsImpl, this)

    /**
     * The reports container.
     *
     * @return The reports container
     */
    FindBugsReports getReports() {
        reports
    }

    /**
     * Configures the reports container.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * findbugsTask {
     *   reports {
     *     xml {
     *       destination "build/findbugs.xml"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    FindBugsReports reports(Closure closure) {
        reports.configure(closure)
    }

    @TaskAction
    void run() {
        String errorProp = 'findbugsError'
		String warningsProp = 'findbugsWarnings'

        Map<String, ?> reportArguments = [:]
        if (reports.enabled) {
            if (reports.enabled.size() == 1) {
                reportArguments.outputFile = reports.firstEnabled.destination
                reportArguments.output = reports.firstEnabled.name
            } else {
                throw new InvalidUserDataException("Findbugs tasks can only have one report enabled, however both the xml and html report are enabled. You need to disable one of them.")
            }
        }

        def antBuilder = services.get(IsolatedAntBuilder)
        antBuilder.withClasspath(getFindbugsClasspath()).execute {
            ant.taskdef(name: 'findbugs', classname: 'edu.umd.cs.findbugs.anttask.FindBugsTask')
            ant.findbugs(debug: logger.isDebugEnabled(), errorProperty: errorProp, warningsProperty: warningsProp, *:reportArguments) {
                getFindbugsClasspath().addToAntBuilder(ant, 'classpath')
                getPluginClasspath().addToAntBuilder(ant, 'pluginList')
                getClasses().addToAntBuilder(ant, 'auxAnalyzepath')
                // FindBugs can't handle either of the following being empty on Windows
                // leads to strange (parsing?) errors like "file src/main/java/-exitcode not found"
                addUnlessEmpty(ant, getClasspath(), 'auxClasspath')
                addUnlessEmpty(ant, getSource(), 'sourcePath')
            }

            if (ant.project.properties[errorProp]) {
                throw new GradleException("FindBugs encountered an error. Run with --debug to get more information.")
            }

            if (ant.project.properties[warningsProp] && !ignoreFailures) {
                if (reportArguments.outputFile) {
                    throw new GradleException("FindBugs rule violations were found. See the report at ${reportArguments.outputFile}.")
                } else {
                    throw new GradleException("FindBugs rule violations were found.")
                }
            }
        }
    }

    protected void addUnlessEmpty(Object ant, FileCollection files, String nodeName) {
        if (!files.empty) {
          files.addToAntBuilder(ant, nodeName)
        }
    }
}