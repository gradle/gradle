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
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.internal.file.FileOperations fileOperations"
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        fails("runInWorker")

        and:
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of interface org.gradle.api.internal.file.FileOperations, or no service of type interface org.gradle.api.internal.file.FileOperations")

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "workers can inject FileSystemOperations service using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.api.file.FileSystemOperations fileOperations
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.file.FileSystemOperations fileOperations"
        fixture.workActionThatCreatesFiles.constructorAction = "this.fileOperations = fileOperations"
        fixture.workActionThatCreatesFiles.action += """
            fileOperations.copy {
                it.from "foo"
                it.into "bar"
            }
            fileOperations.sync {
                it.from "bar"
                it.into "baz"
            }
            fileOperations.delete {
                it.delete "foo"
            }
        """
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """
        file("foo").text = "foo"

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        and:
        file("bar/foo").text == "foo"
        file("baz/foo").text == "foo"
        file("foo").assertDoesNotExist()

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "workers can inject ObjectFactory service using #isolationMode isolation"() {
        fixture.workActionThatCreatesFiles.extraFields += """
            org.gradle.api.model.ObjectFactory objectFactory
            
            interface Foo extends Named { }
        """
        fixture.workActionThatCreatesFiles.constructorArgs = "org.gradle.api.model.ObjectFactory objectFactory"
        fixture.workActionThatCreatesFiles.constructorAction = "this.objectFactory = objectFactory"
        fixture.workActionThatCreatesFiles.action += """
            objectFactory.fileProperty()
            objectFactory.directoryProperty()
            objectFactory.fileCollection()
            objectFactory.fileTree()
            objectFactory.property(String)
            objectFactory.listProperty(String)
            objectFactory.setProperty(String)
            objectFactory.mapProperty(String, String)
            objectFactory.named(Foo, "foo")
            objectFactory.domainObjectContainer(Foo)
        """
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """
        file("foo").text = "foo"

        expect:
        succeeds("runInWorker")

        and:
        assertWorkerExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }
}
