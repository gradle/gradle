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

/**
 * Analyzes code with <a href="http://findbugs.sourceforge.net">FindBugs</a>.
 */
class FindBugs extends SourceTask implements VerificationTask {
    /**
     * The classes to be analyzed.
     */
    @InputFiles FileCollection classes

    /**
     * Compile class path for the classes to be analyzed.
     * The classes on this class path are used during analysis
     * but aren't analyzed themselves.
     */
    @InputFiles FileCollection classpath

    /**
     * Class path holding the FindBugs library.
     */
    @InputFiles FileCollection findbugsClasspath

    /**
     * Class path holding any additional FindBugs plugins.
     */
    @InputFiles FileCollection pluginClasspath

    /**
     * Whether or not to allow the build to continue if there are warnings.
     */
    Boolean ignoreFailures
    
    /**
     * The file in which the FindBugs output will be saved.
     */
    @OutputFile File reportFile
    
    @TaskAction
    void run() {
        String warningsProp = 'findbugsWarnings'

        getReportFile().parentFile.mkdirs()

        ant.taskdef(name: 'findbugs', classname: 'edu.umd.cs.findbugs.anttask.FindBugsTask', classpath: getFindbugsClasspath().asPath)
        ant.findbugs(outputFile: getReportFile(), failOnError: !getIgnoreFailures(), warningsProperty: warningsProp) {
            getFindbugsClasspath().addToAntBuilder(ant, 'classpath')
            getPluginClasspath().addToAntBuilder(ant, 'pluginList')
            getClasspath().addToAntBuilder(ant, 'auxClasspath')
            getSource().addToAntBuilder(ant, 'sourcePath')
            getClasses().asFileTree.files.each { 'class'(location: it) }
        }
        
        if (!ignoreFailures && ant.properties[warningsProp]) {
            throw new GradleException("FindBugs reported warnings. See the report at ${getReportFile()}.")
        }
    }
}