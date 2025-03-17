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

package org.gradle.api.internal.tasks.testing.testng

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.internal.actor.ActorFactory
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.time.FixedClock
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.TestUtil
import spock.lang.Specification

class TestNGTestFrameworkTest extends Specification {
    private ProjectInternal project = ProjectBuilder.builder().build() as ProjectInternal
    private Test testTask = TestUtil.createTask(Test, project)

    void setup() {
        project.ext.sourceCompatibility = "1.7"
    }

    void "creates test class processor"() {
        when:
        def framework = createFramework()
        def processor = framework.getProcessorFactory().create(Mock(IdGenerator), Mock(ActorFactory), FixedClock.create())

        then:
        processor instanceof TestNGTestDefinitionProcessor
        framework.detector
    }

    def "can configure TestNG with an Action"() {
        when:
        testTask.useTestNG { TestNGOptions options ->
            options.suiteName = 'Custom Suite'
        }

        then:
        testTask.options.suiteName.get() == 'Custom Suite'
    }

    private TestNGTestFramework createFramework() {
        TestUtil.objectFactory().newInstance(TestNGTestFramework.class, testTask.getFilter(), testTask.getTemporaryDirFactory(), testTask.getDryRun(), testTask.getReports().getHtml()).tap {
            it.getOptions().getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("testng-reports"))
        }
    }
}
