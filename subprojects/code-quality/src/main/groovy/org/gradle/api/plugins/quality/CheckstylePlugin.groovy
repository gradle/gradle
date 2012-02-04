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

import org.gradle.api.internal.Instantiator
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet

class CheckstylePlugin extends AbstractCodeQualityPlugin<Checkstyle> {
    private Instantiator instantiator
    private CheckstyleExtension extension

    @Override
    protected String getToolName() {
        return "Checkstyle"
    }

    @Override
    protected Class<Checkstyle> getTaskType() {
        return Checkstyle
    }

    @Override
    protected void beforeApply() {
        this.project = project
        instantiator = project.services.get(Instantiator)

        project.plugins.apply(JavaBasePlugin)
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = instantiator.newInstance(CheckstyleExtension)
        project.extensions.checkstyle = extension

        extension.with {
            toolVersion = "5.5"
            sourceSets = project.sourceSets
        }

        extension.conventionMapping.with {
            configFile = { project.file("config/checkstyle/checkstyle.xml") }
            configProperties = { [:] }
            reportsDir = { project.extensions.getByType(ReportingExtension).file("checkstyle") }
        }

        return extension
    }

    @Override
    protected void configureForSourceSet(SourceSet sourceSet, Checkstyle task) {
        task.with {
            description = "Run Checkstyle analysis for ${sourceSet.name} classes"
            classpath = sourceSet.output
        }
        task.conventionMapping.with {
            checkstyleClasspath = {
                def config = project.configurations['checkstyle']
                if (config.dependencies.empty) {
                    project.dependencies {
                        checkstyle "com.puppycrawl.tools:checkstyle:$extension.toolVersion"
                    }
                }
                config
            }
            defaultSource = { sourceSet.allJava }
            configFile = { extension.configFile }
            configProperties = { extension.configProperties }
            reportFile = { new File(extension.reportsDir, "${sourceSet.name}.xml") }
            ignoreFailures = { extension.ignoreFailures }
        }
    }
}
