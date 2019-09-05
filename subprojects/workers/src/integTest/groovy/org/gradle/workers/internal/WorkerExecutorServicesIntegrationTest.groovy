/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal

import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

class WorkerExecutorServicesIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Unroll
    def "workers cannot inject internal services using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.internal.initialization.loadercache.ClassLoaderCache classLoaderCache"
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        fails("runInWorker")

        and:
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of interface org.gradle.api.internal.initialization.loadercache.ClassLoaderCache, or no service of type interface org.gradle.api.internal.initialization.loadercache.ClassLoaderCache")

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "workers can inject public services using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.model.ObjectFactory objectFactory, org.gradle.api.file.FileSystemOperations fileOperations"
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }
}
