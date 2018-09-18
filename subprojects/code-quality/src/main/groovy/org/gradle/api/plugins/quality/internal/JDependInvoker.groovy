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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.plugins.quality.JDepend

abstract class JDependInvoker {
    static void invoke(JDepend jdependTask) {
        def reports = jdependTask.reports
        def antBuilder = jdependTask.antBuilder
        def path = jdependTask.path
        def jdependClasspath = jdependTask.jdependClasspath
        def classesDirs = jdependTask.classesDirs

        Map<String, Object> reportArguments = [:]
        if (reports.enabled.empty) {
            throw new InvalidUserDataException("JDepend tasks must have one report enabled, however neither the xml or text report are enabled for task '$path'. You need to enable one of them")
        } else if (reports.enabled.size() == 1) {
            reportArguments.outputFile = reports.firstEnabled.destination
            reportArguments.format = reports.firstEnabled.name
        } else {
            throw new InvalidUserDataException("JDepend tasks can only have one report enabled, however both the xml and text report are enabled for task '$path'. You need to disable one of them.")
        }

        antBuilder.withClasspath(jdependClasspath).execute {
            ant.taskdef(name: 'jdependreport', classname: 'org.apache.tools.ant.taskdefs.optional.jdepend.JDependTask')
            ant.jdependreport(*:reportArguments, haltonerror: true) {
                classespath {
                    // JDepend will fail if the classesDir does not exist
                    // The directory should have been created by this point
                    classesDirs.findAll({ it.exists() }).each { classesDir ->
                        pathElement(location: classesDir)
                    }
                }
            }
        }
    }
}
