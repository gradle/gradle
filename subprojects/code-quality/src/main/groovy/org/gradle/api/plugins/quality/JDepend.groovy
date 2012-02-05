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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.internal.project.IsolatedAntBuilder

/**
 * Analyzes code with <a href="http://clarkware.com/software/JDepend.html">.
 */
class JDepend extends DefaultTask implements VerificationTask {
    /**
     * The class path containing the JDepend library to be used.
     */
    @InputFiles
    FileCollection jdependClasspath

    /**
     * The directory containing the classes to be analyzed.
     */
    @InputDirectory
    File classesDir

    // workaround for GRADLE-2020
    @SkipWhenEmpty
    File getClassesDir() {
        return classesDir
    }

    /**
     * The file in which the JDepend report will be saved.
     */
    @OutputFile
    File reportFile

    /**
     * Whether or not this task will ignore failures and continue running the build.
     */
    boolean ignoreFailures

    @TaskAction
    void run() {
        def antBuilder = services.get(IsolatedAntBuilder)
        antBuilder.withClasspath(getJdependClasspath()).execute {
            ant.taskdef(name: 'jdepend', classname: 'org.apache.tools.ant.taskdefs.optional.jdepend.JDependTask')
            ant.jdepend(format: 'xml', outputFile: getReportFile(), haltOnError: !getIgnoreFailures()) {
                classespath {
                    pathElement(location: getClassesDir())
                }
            }
        }
    }
}
