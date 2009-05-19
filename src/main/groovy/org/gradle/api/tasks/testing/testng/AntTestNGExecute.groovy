/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.tasks.testing.testng

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.tasks.testing.Test

/**
 * @author Tom Eyckmans
 */

public class AntTestNGExecute {

    private static Logger logger = LoggerFactory.getLogger(AntTestNGExecute);

    public static final String TESTNG_JARS_PATH = 'org.gradle.api.tasks.testing.testng.jars.path'

    void execute(File compiledTestsClassesDir, List classPath, File testResultsDir, File testReportDir, Collection<String> includes, Collection<String> excludes, TestNGOptions options, AntBuilder ant, boolean testReport) {
        ant.mkdir(dir: testResultsDir.absolutePath)

        testngTaskDef(classPath, ant, options)

        Map otherArgs = [
            failureProperty : Test.FAILURES_OR_ERRORS_PROPERTY,
            outputDir : testReportDir.absolutePath,
            workingDir : testResultsDir.absolutePath, 
            haltonfailure : false,
            haltonskipped : false,
            useDefaultListeners : testReport
        ]

        List<File> suites = options.getSuites(testResultsDir)

        logger.info("executing testng tests...");

        ant.testng(otherArgs + options.optionMap()) {
            options.jvmArgs.each {
                jvmarg(value: it)
            }
            options.systemProperties.each {String key, String value ->
                sysproperty(key: key, value: value)
            }
            options.environment.each {String key, String value ->
                env(key: key, value: value)
            }
            classpath {
                classPath.each {
                    pathelement(location : it)
                }
            }
            if ( 'Javadoc'.equalsIgnoreCase(options.annotations) ) {
                sourcedir {
                    options.testResources.each {
                        pathelement(location : it)
                    }
                }
            }
            if ( suites.empty ) {
                classfileset (dir: compiledTestsClassesDir.absolutePath ) {
                    includes.each {
                        include(name: it)
                    }
                    excludes.each {
                        exclude(name: it)
                    }
                }
            }
            else {
                xmlfileset (dir: testResultsDir.absolutePath ){
                    suites.each {
                        include(name: it.name)
                    }
                }
            }
        }

        logger.info("testng tests executed.");
    }

    private void testngTaskDef(List classPath, AntBuilder ant, TestNGOptions options)
    {
        // @TODO there must be a better way of doing this...
        File testngJarFile = null;
        if ( classPath != null && classPath.size() > 0 ) {
            final Iterator<File> classPathIt = classPath.iterator();
            while ( testngJarFile == null && classPathIt.hasNext() ) {
                final File classPathFile = classPathIt.next();
                final String classPathFileName = classPathFile.name;

                if ( classPathFile.isFile() && classPathFileName.startsWith("testng") ) {
                    testngJarFile = classPathFile;
                }
            }
        }

        if ( testngJarFile == null ) {
            throw new IllegalArgumentException("Failed to resolve TestNG dependency");
        }

        if ( testngJarFile.getName().endsWith("jdk14.jar") ) {
            options.javadocAnnotations()
        }

        logger.debug("Using TestNG jar {}", testngJarFile.absolutePath)

        ant.taskdef([resource : "testngtasks", classpath: testngJarFile.absolutePath ])
    }
}
