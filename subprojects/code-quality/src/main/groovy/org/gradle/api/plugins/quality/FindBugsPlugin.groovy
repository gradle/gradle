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

import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.Report
import org.gradle.api.tasks.SourceSet

/**
 * A plugin for the <a href="http://findbugs.sourceforge.net">FindBugs</a> byte code analyzer.
 *
 * <p>
 * Declares a <tt>findbugs</tt> configuration which needs to be configured with the FindBugs library to be used.
 * Additional plugins can be added to the <tt>findbugsPlugins</tt> configuration.
 *
 * <p>
 * For projects that have the Java (base) plugin applied, a {@link FindBugs} task is
 * created for each source set.
 *
 * @see FindBugs
 * @see FindBugsExtension
 */
class FindBugsPlugin extends AbstractCodeQualityPlugin<FindBugs> {
    private FindBugsExtension extension

    @Override
    protected String getToolName() {
        return "FindBugs"
    }

    @Override
    protected Class<FindBugs> getTaskType() {
        return FindBugs
    }

    @Override
    protected void beforeApply() {
        configureFindBugsConfigurations()
    }

    private configureFindBugsConfigurations() {
        project.configurations.create('findbugsPlugins').with {
            visible = false
            transitive = true
            description = 'The FindBugs plugins to be used for this project.'
        }
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.extensions.create("findbugs", FindBugsExtension, project)
        extension.toolVersion = "3.0.0"
        return extension
    }

    @Override
    protected void configureTaskDefaults(FindBugs task, String baseName) {
        task.with {
            pluginClasspath = project.configurations['findbugsPlugins']
        }
        def config = project.configurations['findbugs']
        config.incoming.beforeResolve {
            if (config.dependencies.empty) {
                config.dependencies.add(project.dependencies.create("com.google.code.findbugs:findbugs:$extension.toolVersion"))
            }
        }
        task.conventionMapping.with {
            findbugsClasspath = { config }
            ignoreFailures = { extension.ignoreFailures }
            effort = { extension.effort }
            reportLevel = { extension.reportLevel }
            visitors = { extension.visitors }
            omitVisitors = { extension.omitVisitors }
            excludeFilterConfig = { extension.excludeFilterConfig }
            includeFilterConfig = { extension.includeFilterConfig }
 
        }
        task.reports.all { Report report ->
            report.conventionMapping.with {
                enabled = { report.name == "xml" }
                destination = { new File(extension.reportsDir, "${baseName}.${report.name}") }
            }
        }
    }

    @Override
    protected void configureForSourceSet(SourceSet sourceSet, FindBugs task) {
        task.with {
            description = "Run FindBugs analysis for ${sourceSet.name} classes"
        }
        task.source = sourceSet.allJava
        task.conventionMapping.with {
            classes = {
                // the simple "classes = sourceSet.output" may lead to non-existing resources directory
                // being passed to FindBugs Ant task, resulting in an error
                project.fileTree(sourceSet.output.classesDir) {
                    builtBy sourceSet.output
                }
            }
            classpath = { sourceSet.compileClasspath }
        }
    }
}
