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
package org.gradle.api.plugins.quality

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.api.GradleException
import org.gradle.util.DeprecationLogger
import org.gradle.api.internal.project.IsolatedAntBuilder

/**
 * Runs Checkstyle against some source files.
 */
class Checkstyle extends SourceTask implements VerificationTask {
    /**
     * The class path containing the Checkstyle library to be used.
     */
    @InputFiles
    FileCollection checkstyleClasspath

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @InputFiles
    FileCollection classpath

    /**
     * The Checkstyle configuration file to use.
     */
    @InputFile
    File configFile

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration
     * file. Defaults to <tt>empty map</tt>.
     */
    @Input
    @Optional
    Map<String, Object> configProperties = [:]

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration
     * file.
     * 
     * @deprecated renamed to <tt>configProperties</tt>
     */
    @Deprecated
    Map<String, Object> getProperties() {
        DeprecationLogger.nagUserOfReplacedProperty("properties", "configProperties")
        getConfigProperties()
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration
     * file.
     *
     * @deprecated renamed to <tt>configProperties</tt>
     */
    @Deprecated
    void setProperties(Map<String, Object> properties) {
        DeprecationLogger.nagUserOfReplacedProperty("properties", "configProperties")
        setConfigProperties(properties)
    }

    /**
     * The file in which the XML report will be saved.
     */
    @OutputFile
    File reportFile

    /**
     * @deprecated renamed to <tt>reportFile</tt>
     */
    @Deprecated
    File getResultFile() {
        DeprecationLogger.nagUserOfReplacedProperty("resultFile", "reportFile")
        getReportFile()
    }

    /**
     * @deprecated renamed to <tt>reportFile</tt>
     */
    @Deprecated
    void setResultFile(File file) {
        DeprecationLogger.nagUserOfReplacedProperty("resultFile", "reportFile")
        setReportFile(file)
    }

    /**
     * Whether or not this task will ignore failures and continue running the build.
     */
    boolean ignoreFailures

    @TaskAction
    public void run() {
        def propertyName = "org.gradle.checkstyle.violations"
        def antBuilder = services.get(IsolatedAntBuilder)
        antBuilder.withClasspath(getCheckstyleClasspath()).execute {
            ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.CheckStyleTask')

            ant.checkstyle(config: getConfigFile(), failOnViolation: false, failureProperty: propertyName) {
                getSource().addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                getClasspath().addToAntBuilder(ant, 'classpath')
                formatter(type: 'plain', useFile: false)
                formatter(type: 'xml', toFile: getReportFile())
                getConfigProperties().each { key, value ->
                    property(key: key, value: value.toString())
                }
            }

            if (!getIgnoreFailures() && ant.project.properties[propertyName]) {
                throw new GradleException("Checkstyle rule violations were found. See the report at ${getReportFile()}.")
            }
        }
    }
}
