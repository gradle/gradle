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

class FileSystemSensitiveArchivesInterceptor extends AbstractMultiTestInterceptor {

    protected FileSystemSensitiveArchivesInterceptor(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        add(new FileSystemSensitiveArchivesExecution(false))
        add(new FileSystemSensitiveArchivesExecution(true))
    }

    private static class FileSystemSensitiveArchivesExecution extends AbstractMultiTestInterceptor.Execution {

        private final boolean withFileSystemSensitiveArchives

        FileSystemSensitiveArchivesExecution(boolean withFileSystemSensitiveArchives) {
            this.withFileSystemSensitiveArchives = withFileSystemSensitiveArchives
        }

        @Override
        String toString() {
            return getDisplayName()
        }

        @Override
        protected String getDisplayName() {
            return withFileSystemSensitiveArchives ? "with file system sensitive archives" : ""
        }

        protected void before(IMethodInvocation invocation) {
            if (withFileSystemSensitiveArchives) {
                AbstractIntegrationSpec instance = invocation.instance as AbstractIntegrationSpec
                def initScript = instance.testDirectory.file('file-system-sensitive-archives-init.gradle')
                initScript.text = """
                    gradle.lifecycle.beforeProject {
                        tasks.withType(AbstractArchiveTask).configureEach {
                            preserveFileTimestamps = true
                            reproducibleFileOrder = false
                            useFileSystemPermissions()
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
