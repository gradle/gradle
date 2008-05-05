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

package org.gradle.api.tasks.testing

import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
//todo: assertions for fork and permissions for non fork
//todo: offer all the power of ant selectors
//todo: Find a more stable way to find the ant junit jars
class AntJunit {
    private static Logger logger = LoggerFactory.getLogger(AntJunit)

    static final String CLASSPATH_ID = 'runtests.classpath'

    static final String FAILURES_OR_ERRORS_PROPERTY = 'org.gradle.api.tasks.testing.failuresOrErrors'

    void execute(File compiledTestsClassesDir, List classPath, File testResultsDir, List includes, List excludes, JunitOptions junitOptions, AntBuilder ant) {
        ant.mkdir(dir: testResultsDir.absolutePath)
        createAntClassPath(ant, classPath + GradleUtil.antJunitJarFiles)
        Map otherArgs = [
                includeantruntime: 'false',
                errorproperty: FAILURES_OR_ERRORS_PROPERTY,
                failureproperty: FAILURES_OR_ERRORS_PROPERTY
        ]
        ant.junit(otherArgs + junitOptions.optionMap()) {
            junitOptions.forkOptions.jvmArgs.each {
                jvmarg(value: it)
            }
            junitOptions.systemProperties.each {String key, String value ->
                sysproperty(key: key, value: value)
            }
            junitOptions.forkOptions.environment.each {String key, String value ->
                env(key: key, value: value)
            }
            formatter(junitOptions.formatterOptions.optionMap())
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
    }

    private void createAntClassPath(AntBuilder ant, List classpath) {
        ant.path(id: CLASSPATH_ID) {
            classpath.each {
                logger.debug("Add $it to Ant classpath!")
                pathelement(location: it)
            }
        }
    }
}
