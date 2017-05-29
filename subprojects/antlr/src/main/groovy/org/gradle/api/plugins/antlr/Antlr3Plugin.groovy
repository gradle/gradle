/*
 * Copyright 2012 the original author or authors.
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




package org.gradle.api.plugins.antlr;


import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.util.ConfigureUtil
import org.gradle.api.plugins.antlr.internal.AntlrSourceVirtualDirectoryImpl

/**
 * A plugin for adding Antlr V3 support to {@link JavaPlugin java projects}.
 *
 * @author Strong Liu
 */
public class Antlr3Plugin implements Plugin<ProjectInternal> {
    private ProjectInternal project;
    private static final String CONFIGURATION_NAME = "antlr3";

    Antlr3Extension extension;

    void apply(ProjectInternal project) {
        this.project = project
        project.plugins.apply("java")

        createConfigurations()

        extension = project.extensions.create("antlr3", Antlr3Extension)


        project.convention.getPlugin(JavaPluginConvention).sourceSets.all { SourceSet sourceSet ->
            final AntlrSourceVirtualDirectory antlrDirectoryDelegate = new AntlrSourceVirtualDirectoryImpl(sourceSet.displayName, project.fileResolver)

            antlrDirectoryDelegate.antlr.srcDir("src/${sourceSet.name}/antlr");

            new DslObject(sourceSet).convention.plugins.put(
                    AntlrSourceVirtualDirectory.NAME, antlrDirectoryDelegate);


            final String taskName = sourceSet.getTaskName("generate", "GrammarSource");
            final File outputDir = project.file("${project.buildDir}/generated-src/antlr/${sourceSet.name}")

            sourceSet.with {
                allSource.source(antlrDirectoryDelegate.antlr)
                java.srcDir(outputDir)
            }

            project.tasks.getByName(sourceSet.compileJavaTaskName).dependsOn(taskName)

            Antlr3Task antlr3Task = project.tasks.add(taskName, Antlr3Task)
            antlr3Task.outputDirectory = outputDir
            File baseSourcePath = project.file("src/${sourceSet.name}/antlr");
            antlr3Task.baseSourcePath = baseSourcePath
            antlr3Task.grammarFiles = antlrDirectoryDelegate.antlr.files
            antlr3Task.conventionMapping.with {
                args = { buildExtensionArgs(antlrDirectoryDelegate, outputDir, baseSourcePath) }
                antlr3Classpath = {
                    def config = project.configurations[CONFIGURATION_NAME]
                    if (config.dependencies.empty) {
                        project.dependencies {
                            antlr3 "org.antlr:antlr:$extension.version"
                        }
                    }
                    config
                }
            }
        }
    }


    protected Configuration createConfigurations() {
        return project.configurations.add(CONFIGURATION_NAME).with {
            visible = false
            transitive = true
            description = "The ${CONFIGURATION_NAME} libraries to be used for this project."
            // Don't need these things, they're provided by the runtime
            exclude group: 'ant', module: 'ant'
            exclude group: 'org.apache.ant', module: 'ant'
            exclude group: 'org.apache.ant', module: 'ant-launcher'
            exclude group: 'org.codehaus.groovy', module: 'groovy'
            exclude group: 'org.codehaus.groovy', module: 'groovy-all'
            exclude group: 'org.slf4j', module: 'slf4j-api'
            exclude group: 'org.slf4j', module: 'jcl-over-slf4j'
            exclude group: 'org.slf4j', module: 'log4j-over-slf4j'
            exclude group: 'commons-logging', module: 'commons-logging'
            exclude group: 'log4j', module: 'log4j'
        }
    }



    private List buildExtensionArgs(antlrDirectoryDelegate, outputDir, File baseSourcePath) {
        def arguments = []
        if (isTrue(extension.forceAllFilesToOutputDir)) {
            arguments << "-fo"
        } else {
            arguments << "-o"
        }
        arguments << outputDir.absolutePath
        arguments << "-lib"
        if (extension.lib != null && extension.lib.exists()) {
            arguments << extension.lib.absolutePath
        } else {
            //we set lib to the base out put dir
            arguments << outputDir.absolutePath
        }

        keyArgs.each { arg ->
            if (isTrue(extension."${arg}")) arguments << "-" + caseConvert(arg)
        }
        keyValueArgs.each { arg ->
            if (extension."${arg}" != null) {
                arguments << "-" + caseConvert(arg)
                arguments << extension."${arg}"
            }
        }
        if (extension.messageFormat != null) {
            arguments << "-message-format"
            arguments << extension.messageFormat
        }
        antlrDirectoryDelegate.antlr.files.each { File grammarFile ->
            arguments << grammarFile.absolutePath.substring(baseSourcePath.absolutePath.length() + 1)
        }
        return arguments
    }

    def keyValueArgs = ["xmaxswitchcaselabels", "xmaxinlinedfastates", "language", "xminswitchalts", "xm", "xmaxdfaedges", "xconversiontimeout"]

    def keyArgs = ["nfa", "dfa", "debug", "trace", "report", "profile", "print", "depend",
            "verbose", "make", "xgrtree", "xdfa", "xnoprune", "xnocollapse", "xdbgconversion",
            "xmultithreaded", "xnomergestopstates", "xdfaverbose", "xwatchconversion", "xdbgST", "xnfastates", "xsavelexer"];

    private boolean isTrue(Boolean arg) {
        return arg != null && arg
    }

    private String caseConvert(String str) {
        if (str.startsWith("x")) {
            return "X" + str.substring(1, str.length())
        }
        return str;
    }


}


