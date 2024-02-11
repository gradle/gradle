/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r75

import org.gradle.integtests.tooling.fixture.Snippets
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener

@TargetGradleVersion('>=7.5')
class CustomTestTaskProgressEventCrossVersionTest extends ToolingApiSpecification {

    def "receives progress events from custom test task implementations"() {
        setup:
        Snippets.customTestTask(buildFile, file('buildSrc'))

        ValidatingProgressEventListener validatingListener = new ValidatingProgressEventListener()

        when:
        withConnection {
            it.newBuild().forTasks('myTestTask').addProgressListener(validatingListener).run()
        }

        then:
        validatingListener.receivedCustomTestTaskProgressEvents()
    }

    class ValidatingProgressEventListener implements ProgressListener {

        ProgressEvent suiteEvent = null
        ProgressEvent testEvent = null

        @Override
        void statusChanged(ProgressEvent event) {
            if (event.descriptor.displayName == "Test suite 'MyCustomTestRoot'") {
                suiteEvent = event
            } else if (event.descriptor.displayName == 'org.my.MyClass descriptor') {
                testEvent = event
            }
        }
        boolean receivedCustomTestTaskProgressEvents() {
            suiteEvent != null && testEvent != null
        }
    }
}

