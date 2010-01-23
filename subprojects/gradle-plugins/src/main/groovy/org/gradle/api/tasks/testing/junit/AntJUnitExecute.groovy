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

package org.gradle.api.tasks.testing.junit

import org.gradle.api.tasks.testing.AntTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.tasks.testing.TestListener
import org.gradle.listener.remote.RemoteReceiver
import org.gradle.listener.ListenerBroadcast
import org.gradle.api.internal.ClassPathRegistry

/**
 * @author Hans Dockter
 */
//todo: assertions for fork and permissions for non fork
//todo: offer all the power of ant selectors
//todo: Find a more stable way to find the ant junit jars
class AntJUnitExecute {
    private static Logger logger = LoggerFactory.getLogger(AntJUnitExecute)
    private static final String CLASSPATH_ID = 'runtests.classpath'
    private final ClassPathRegistry classPathRegistry

    def AntJUnitExecute(ClassPathRegistry classPathRegistry) {
        this.classPathRegistry = classPathRegistry
    }

    void execute(File compiledTestsClassesDir, List classPath, File testResultsDir, Collection<String> includes,
                 Collection<String> excludes, JUnitOptions junitOptions, AntBuilder ant,
                 ListenerBroadcast<TestListener> testListenerBroadcaster) {
        ant.mkdir(dir: testResultsDir.absolutePath)
        createAntClassPath(ant, classPath + classPathRegistry.getClassPathFiles("ANT_JUNIT") + classPathRegistry.getClassPathFiles("TEST_LISTENER"))
        Map otherArgs = [
                includeantruntime: 'false',
                errorproperty: AntTest.FAILURES_OR_ERRORS_PROPERTY,
                failureproperty: AntTest.FAILURES_OR_ERRORS_PROPERTY
        ]

        final RemoteReceiver remoteReceiver = new RemoteReceiver(TestListener.class, testListenerBroadcaster);
        logger.debug("Listening for test listener events on {}.", remoteReceiver.localAddress)
        try {
            ant.junit(otherArgs + junitOptions.optionMap()) {
                junitOptions.forkOptions.jvmArgs.each {
                    jvmarg(value: it)
                }
                junitOptions.systemProperties.each {String key, value ->
                    sysproperty(key: key, value: value)
                }
                junitOptions.forkOptions.environment.each {String key, value ->
                    env(key: key, value: value)
                }
                formatter(junitOptions.formatterOptions.optionMap())
                sysproperty(key: TestListenerFormatter.SERVER_ADDRESS, value: remoteReceiver.localAddress)
                formatter(type: 'plain', classname: TestListenerFormatter.class.name)
                batchtest(todir: testResultsDir.absolutePath) {
                    fileset(dir: compiledTestsClassesDir.absolutePath) {
                        includes.each {
                            include(name: it)
                        }
                        excludes.each {
                            exclude(name: it)
                        }
                    }
                }
                classpath() {
                    path(refid: CLASSPATH_ID)
                }
            }
        } finally {
            remoteReceiver.stop();
        }
    }

    private void createAntClassPath(AntBuilder ant, List classpath) {
        ant.path(id: CLASSPATH_ID) {
            classpath.each {
                logger.debug("Add {} to Ant classpath!", it)
                pathelement(location: it)
            }
        }
    }
}
