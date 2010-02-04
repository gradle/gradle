/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.tasks.testing.AntTest
import org.gradle.listener.ListenerBroadcast
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.testing.execution.ant.AbstractBatchTestClassProcessor

/**
 * @author Tom Eyckmans
 */

public class AntTestNGExecute extends AbstractBatchTestClassProcessor {
    public static final String TESTNG_JARS_PATH = 'org.gradle.api.tasks.testing.testng.jars.path'
    private static Logger logger = LoggerFactory.getLogger(AntTestNGExecute);
    private final File testClassesDir
    private final Iterable<File> classPath
    private final File testResultsDir
    private final File testReportDir
    private final TestNGOptions options
    private final AntBuilder ant
    private final ListenerBroadcast<TestListener> testListenerBroadcast

    def AntTestNGExecute(File compiledTestsClassesDir, Iterable classPath, File testResultsDir, File testReportDir,
                         TestNGOptions options, AntBuilder ant, ListenerBroadcast<TestListener> testListenerBroadcaster) {
        this.testClassesDir = compiledTestsClassesDir
        this.classPath = classPath
        this.testResultsDir = testResultsDir
        this.testReportDir = testReportDir
        this.options = options
        this.ant = ant
        this.testListenerBroadcast = testListenerBroadcaster
    }

    /**
     * This method contains several comments of the form
     *         TODO TestNG Listeners: [<stuff>]
     * Once we can implement TestNG listeners, replace the lines with <stuff> to make it work again.
     * For a discussion of why this is disabled, see   {@link TestNGListenerAdapter}  .
     */
    protected void executeTests() {
        ant.mkdir(dir: testResultsDir.absolutePath)

        List useClassPath = classPath as List
        //TODO TestNG Listeners: [useClassPath += BootstrapUtil.gradleTestListenerFiles]

        testngTaskDef(useClassPath, ant, options)

        Map otherArgs = [
                failureProperty: AntTest.FAILURES_OR_ERRORS_PROPERTY,
                outputDir: testReportDir.absolutePath,
                workingDir: testResultsDir.absolutePath,
                haltonfailure: false,
                haltonskipped: false,
                //TODO TestNG Listeners: [listeners: TestNGListenerAdapter.class.name]
        ]

        List<File> suites = options.getSuites(testResultsDir)

        logger.info("executing testng tests...");

        //TODO TestNG Listeners: [RemoteReceiver remoteReceiver = new RemoteReceiver(testListenerBroadcaster, null)]
        ant.testng(otherArgs + options.optionMap()) {
            options.jvmArgs.each {
                jvmarg(value: it)
            }
            //TODO TestNG Listeners: [sysproperty(key: TestNGListenerAdapter.PORT_VMARG, value: remoteReceiver.getBoundPort())]
            options.systemProperties.each {String key, String value ->
                sysproperty(key: key, value: value)
            }
            options.environment.each {String key, String value ->
                env(key: key, value: value)
            }
            classpath {
                useClassPath.each {
                    pathelement(location: it)
                }
            }
            if ('Javadoc'.equalsIgnoreCase(options.annotations)) {
                sourcedir {
                    options.testResources.each {
                        pathelement(location: it)
                    }
                }
            }
            if (suites.empty) {
                classfileset(dir: testClassesDir.absolutePath) {
                    testClassFileNames.each {
                        include(name: it)
                    }
                }
            } else {
                xmlfileset(dir: testResultsDir.absolutePath) {
                    suites.each {
                        include(name: it.name)
                    }
                }
            }
        }

        logger.info("testng tests executed.");
    }

    private void testngTaskDef(List classPath, AntBuilder ant, TestNGOptions options) {
        // @TODO there must be a better way of doing this...
        File testngJarFile = null;
        if (classPath != null && classPath.size() > 0) {
            final Iterator<File> classPathIt = classPath.iterator();
            while (testngJarFile == null && classPathIt.hasNext()) {
                final File classPathFile = classPathIt.next();
                final String classPathFileName = classPathFile.name;

                if (classPathFile.isFile() && classPathFileName.startsWith("testng")) {
                    testngJarFile = classPathFile;
                }
            }
        }

        if (testngJarFile == null) {
            throw new IllegalArgumentException("Failed to resolve TestNG dependency");
        }

        if (testngJarFile.getName().endsWith("jdk14.jar")) {
            options.javadocAnnotations()
        }

        logger.debug("Using TestNG jar {}", testngJarFile.absolutePath)

        ant.taskdef([resource: "testngtasks", classpath: testngJarFile.absolutePath])
    }
}
