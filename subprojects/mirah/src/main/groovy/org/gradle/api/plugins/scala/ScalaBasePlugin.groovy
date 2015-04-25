/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.mirah
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultMirahSourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.MirahRuntime
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.mirah.MirahCompile
import org.gradle.api.tasks.mirah.MirahDoc
import org.gradle.language.mirah.internal.toolchain.DefaultMirahToolProvider

import javax.inject.Inject

class MirahBasePlugin implements Plugin<Project> {
    static final String ZINC_CONFIGURATION_NAME = "zinc"

    static final String SCALA_RUNTIME_EXTENSION_NAME = "mirahRuntime"

    private final FileResolver fileResolver

    private Project project
    private MirahRuntime mirahRuntime

    @Inject
    MirahBasePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        this.project = project
        project.pluginManager.apply(JavaBasePlugin)
        def javaPlugin = project.plugins.getPlugin(JavaBasePlugin.class)

        configureConfigurations(project)
        configureMirahRuntimeExtension()
        configureCompileDefaults()
        configureSourceSetDefaults(javaPlugin)
        configureMirahdoc()
    }

    private void configureConfigurations(Project project) {
        project.configurations.create(ZINC_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Zinc incremental compiler to be used for this Mirah project.")
    }

    private void configureMirahRuntimeExtension() {
        mirahRuntime = project.extensions.create(SCALA_RUNTIME_EXTENSION_NAME, MirahRuntime, project)
    }

    private void configureSourceSetDefaults(JavaBasePlugin javaPlugin) {
        project.convention.getPlugin(JavaPluginConvention.class).sourceSets.all { SourceSet sourceSet ->
            sourceSet.convention.plugins.mirah = new DefaultMirahSourceSet(sourceSet.displayName, fileResolver)
            sourceSet.mirah.srcDir { project.file("src/$sourceSet.name/mirah") }
            sourceSet.allJava.source(sourceSet.mirah)
            sourceSet.allSource.source(sourceSet.mirah)
            sourceSet.resources.filter.exclude { FileTreeElement element -> sourceSet.mirah.contains(element.file) }

            configureMirahCompile(javaPlugin, sourceSet)
            configureMirahConsole(sourceSet)
        }
    }

    private void configureMirahCompile(JavaBasePlugin javaPlugin, SourceSet sourceSet) {
        def taskName = sourceSet.getCompileTaskName('mirah')
        def mirahCompile = project.tasks.create(taskName, MirahCompile)
        mirahCompile.dependsOn sourceSet.compileJavaTaskName
        javaPlugin.configureForSourceSet(sourceSet, mirahCompile)
        mirahCompile.description = "Compiles the $sourceSet.mirah."
        mirahCompile.source = sourceSet.mirah
        project.tasks[sourceSet.classesTaskName].dependsOn(taskName)

        // cannot use convention mapping because the resulting object won't be serializable
        // cannot compute at task execution time because we need association with source set
        project.gradle.projectsEvaluated {
            mirahCompile.mirahCompileOptions.incrementalOptions.with {
                if (!analysisFile) {
                    analysisFile = new File("$project.buildDir/tmp/mirah/compilerAnalysis/${mirahCompile.name}.analysis")
                }
                if (!publishedCode) {
                    def jarTask = project.tasks.findByName(sourceSet.getJarTaskName())
                    publishedCode = jarTask?.archivePath
                }
            }
        }
    }

    private void configureMirahConsole(SourceSet sourceSet) {
        def taskName = sourceSet.getTaskName("mirah", "Console")
        def mirahConsole = project.tasks.create(taskName, JavaExec)
        mirahConsole.dependsOn(sourceSet.runtimeClasspath)
        mirahConsole.description = "Starts a Mirah REPL with the $sourceSet.name runtime class path."
        mirahConsole.main = "mirah.tools.nsc.MainGenericRunner"
        mirahConsole.conventionMapping.classpath = { mirahRuntime.inferMirahClasspath(sourceSet.runtimeClasspath) }
        mirahConsole.systemProperty("mirah.usejavacp", true)
        mirahConsole.standardInput = System.in
        mirahConsole.conventionMapping.jvmArgs = { ["-classpath", sourceSet.runtimeClasspath.asPath] }
    }

    private void configureCompileDefaults() {
        project.tasks.withType(MirahCompile.class) { MirahCompile compile ->
            compile.conventionMapping.mirahClasspath = { mirahRuntime.inferMirahClasspath(compile.classpath) }
            compile.conventionMapping.zincClasspath = {
                def config = project.configurations[ZINC_CONFIGURATION_NAME]
                if (!compile.mirahCompileOptions.useAnt && config.dependencies.empty) {
                    project.dependencies {
                        zinc("com.typesafe.zinc:zinc:$DefaultMirahToolProvider.DEFAULT_ZINC_VERSION")
                    }
                }
                config
            }
        }
    }

    private void configureMirahdoc() {
        project.tasks.withType(MirahDoc) { MirahDoc mirahDoc ->
            mirahDoc.conventionMapping.destinationDir = { project.file("$project.docsDir/mirahdoc") }
            mirahDoc.conventionMapping.title = { project.extensions.getByType(ReportingExtension).apiDocTitle }
            mirahDoc.conventionMapping.mirahClasspath = { mirahRuntime.inferMirahClasspath(mirahDoc.classpath) }
        }
    }
}