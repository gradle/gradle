/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.actor.Actor
import org.gradle.internal.actor.ActorFactory
import org.gradle.internal.operations.BuildOperationWorkerRegistry
import org.gradle.process.internal.worker.WorkerProcessFactory
import spock.lang.Specification

class DefaultTestExecuterTest extends Specification {

    TestResultProcessor testResultProcessor = Mock()
    Test testTask = Mock()
    ActorFactory actorFactory = Mock()
    WorkerProcessFactory workerFactory = Mock()
    ModuleRegistry moduleRegistry = Mock()
    TestFramework testFramework = Mock()
    TestResultProcessor resultProcessor = Mock()
    Actor resultProcessorActor = Mock()
    TestFrameworkDetector testFrameworkTestDetector = Mock()
    File testClassesDir = Mock()
    FileCollection testClasspath = Mock()
    Project project = Mock()
    BuildOperationWorkerRegistry buildOperationWorkerRegistry = Mock()

    DefaultTestExecuter executer = new DefaultTestExecuter(workerFactory, actorFactory, moduleRegistry, buildOperationWorkerRegistry)

    def setup() {
        _ * testTask.testFramework >> testFramework
        _ * testTask.getCandidateClassFiles() >> Mock(FileTree)
        _ * testTask.getPath() >> ':'
        _ * testTask.getProject() >> project
        _ * actorFactory.createActor(_) >> resultProcessorActor
        _ * resultProcessorActor.getProxy(_) >> resultProcessor
        _ * testTask.isScanForTestClasses() >> true
        _ * testFramework.getDetector() >> testFrameworkTestDetector
    }

    def "testClassDirectory for testclassdetector is configured before executing"() {
        when:
        executer.execute(testTask, testResultProcessor);
        then:
        1 * testFramework.getDetector() >> testFrameworkTestDetector
        1 * testTask.getTestClassesDir() >> testClassesDir
        1 * testFrameworkTestDetector.setTestClassesDirectory(testClassesDir);
    }

    def "testClasspath for testclassdetector is configured before executing"() {
        when:
        executer.execute(testTask, testResultProcessor);
        then:
        1 * testTask.getClasspath() >> testClasspath
        1 * testFrameworkTestDetector.setTestClasspath(testClasspath)
    }
}
