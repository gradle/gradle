/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.archives

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

class ReproducibleArchivesInterceptor extends AbstractMultiTestInterceptor {

    protected ReproducibleArchivesInterceptor(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        add(new ReproducibleArchivesExecution(false))
        add(new ReproducibleArchivesExecution(true))
    }

    private static class ReproducibleArchivesExecution extends AbstractMultiTestInterceptor.Execution {

        private final boolean withReproducibleArchives

        ReproducibleArchivesExecution(boolean withReproducibleArchives) {
            this.withReproducibleArchives = withReproducibleArchives
        }

        @Override
        String toString() {
            return getDisplayName()
        }

        @Override
        protected String getDisplayName() {
            return withReproducibleArchives ? "with reproducible archives" : "without reproducible archives"
        }

        protected void before(IMethodInvocation invocation) {
            if (withReproducibleArchives) {
                AbstractIntegrationSpec instance = invocation.instance as AbstractIntegrationSpec
                def initScript = instance.testDirectory.file('reproducible-archives-init.gradle')
                initScript.text = """
                        rootProject { prj ->
                            allprojects {
                                tasks.withType(AbstractArchiveTask) {
                                    preserveFileTimestamps = false
                                    reproducibleFileOrder = true
                                }
                            }
                        }
                    """.stripIndent()
                instance.executer.beforeExecute {
                    usingInitScript(initScript)
                }
            }
        }
    }
}
