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
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.tasks.SkipWhenEmpty

/**
 * Analyzes code with <a href="http://findbugs.sourceforge.net">FindBugs</a>.
 */
class FindBugs extends SourceTask implements VerificationTask {
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
    
    /**
     * The file in which the FindBugs output will be saved.
     */
    @OutputFile
    File reportFile
    
    @TaskAction
    void run() {
        String errorProp = 'findbugsError'
		String warningsProp = 'findbugsWarnings'

        getReportFile().parentFile.mkdirs()

		ant.taskdef(name: 'findbugs', classname: 'edu.umd.cs.findbugs.anttask.FindBugsTask', classpath: getFindbugsClasspath().asPath)
		ant.findbugs(debug: logger.isDebugEnabled(), outputFile: getReportFile(), errorProperty: errorProp, warningsProperty: warningsProp) {
            getFindbugsClasspath().addToAntBuilder(ant, 'classpath')
            getPluginClasspath().addToAntBuilder(ant, 'pluginList')
            getClasses().addToAntBuilder(ant, 'auxAnalyzepath')
            // FindBugs can't handle either of the following being empty on Windows
            // leads to strange (parsing?) errors like "file src/main/java/-exitcode not found"
            addUnlessEmpty(getClasspath(), 'auxClasspath')
            addUnlessEmpty(getSource(), 'sourcePath')
        }
		
        if (ant.properties[errorProp]) {
            throw new GradleException("FindBugs encountered an error. Run with --debug to get more information.")
        }
      
		if (ant.properties[warningsProp] && !ignoreFailures) {
			throw new GradleException("FindBugs rule violations were found. See the report at ${getReportFile()}.")
		}
    }

    protected void addUnlessEmpty(FileCollection files, String nodeName) {
        if (!files.empty) {
          files.addToAntBuilder(ant, nodeName)
        }
    }
}