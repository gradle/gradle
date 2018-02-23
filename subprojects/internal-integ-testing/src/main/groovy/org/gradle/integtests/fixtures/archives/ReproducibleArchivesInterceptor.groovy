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
import org.spockframework.runtime.extension.AbstractMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.NameProvider

class ReproducibleArchivesInterceptor extends AbstractMethodInterceptor {

    boolean reproducibleArchives

    @Override
    void interceptFeatureExecution(IMethodInvocation invocation) throws Throwable {
        reproducibleArchives = false
        invocation.proceed()
        reproducibleArchives = true
        invocation.proceed()
    }

    @Override
    void interceptIterationExecution(IMethodInvocation invocation) throws Throwable {
        // Allow tests to check at runtime if reproducible archives is switched on or not
        invocation.instance.metaClass.reproducibleArchives = reproducibleArchives
        if (reproducibleArchives) {
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

        invocation.proceed()
    }

    NameProvider<IterationInfo> nameProvider(final NameProvider<IterationInfo> delegate) {
        return new NameProvider<IterationInfo>() {
            @Override
            String getName(IterationInfo iterationInfo) {
                return (delegate?.getName(iterationInfo) ?: iterationInfo.parent.name) +
                    (reproducibleArchives ? ' [reproducible archives]' : '')
            }
        }

    }
}
