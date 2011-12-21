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
package org.gradle.api.plugins.quality.internal

import org.gradle.api.Project
import org.gradle.api.plugins.quality.JDependPlugin

/**
 * A helper class to call the JDepend Ant task.
 * 
 * @author Andrew Oberstar
 * @version 0.1.0
 * @version 0.1.0
 */
class AntJDepend {
    /**
     * Calls the JDepend Ant task
     * @param ant the ant builder to use
     * @param project the project this is being executed for
     * @param classesDir the directory where the classes to analyze are located
     * @param resultsFile the file to generate the results XML to
     * @param ignoreFailures whether to fail the task if there is an error
     * @return
     */
    def call(AntBuilder ant, Project project, File classesDir, File resultsFile, boolean ignoreFailures) {
        ant.taskdef(name:'jdepend', classname:'org.apache.tools.ant.taskdefs.optional.jdepend.JDependTask', classpath:project.configurations[JDependPlugin.JDEPEND_CONFIGURATION_NAME].asPath)
        ant.jdepend(format:'xml', outputFile:resultsFile, haltOnError:!ignoreFailures) {
            classespath {
                pathElement(location:classesDir)
            }
        }
    }
}
