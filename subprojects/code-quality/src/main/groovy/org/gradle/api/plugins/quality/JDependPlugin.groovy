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

import org.gradle.api.Plugin
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.Report
import org.gradle.api.tasks.SourceSet

/**
 * <p>
 * A {@link Plugin} that generates design quality metrics by
 * scanning your source packages.  This is done using the JDepend
 * tool.
 * </p>
 * <p>
 * This plugin will automatically generate a task for each Java source set.
 * </p>
 * See <a href="http://www.clarkware.com/software/JDepend.html">JDepend</a> for more information.
 *
 * @see JDependExtension
 * @see JDepend
 */
class JDependPlugin extends AbstractCodeQualityPlugin<JDepend> {
    private JDependExtension extension

    @Override
    protected String getToolName() {
        return "JDepend"
    }

    @Override
    protected Class<JDepend> getTaskType() {
        return JDepend
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.extensions.create("jdepend", JDependExtension)
        extension.with {
            toolVersion = "2.9.1"
        }
        return extension
    }

    @Override
    protected void configureTaskDefaults(JDepend task, String baseName) {
        def config = project.configurations['jdepend']
        config.incoming.beforeResolve {
            if (config.dependencies.empty) {
                project.dependencies {
                    jdepend "jdepend:jdepend:$extension.toolVersion"
                    jdepend("org.apache.ant:ant-jdepend:1.8.2")
                }
            }
        }
        task.conventionMapping.with {
            jdependClasspath = { config }
        }
        task.reports.all { Report report ->
            report.conventionMapping.with {
                enabled = { report.name == "xml" }
                destination = {
                    def fileSuffix = report.name == 'text' ? 'txt' : report.name
                    new File(extension.reportsDir, "${baseName}.${fileSuffix}")
                }
            }
        }
    }

    @Override
    protected void configureForSourceSet(SourceSet sourceSet, JDepend task) {
        task.with {
            dependsOn(sourceSet.output)
            description = "Run JDepend analysis for ${sourceSet.name} classes"
        }
        task.conventionMapping.with {
            classesDir = { sourceSet.output.classesDir }
        }
    }
}
