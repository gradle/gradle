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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

class NotifyingSettingsProcessorTest extends Specification {

    def "delegates to decorated script plugin via build operation"() {
        given:
        def buildOperationExecutor = new TestBuildOperationExecutor()
        def settingsProcessor = Mock(SettingsProcessor)
        def gradleInternal = Mock(GradleInternal)
        def settingsLocation = Mock(SettingsLocation)
        def buildOperationScriptPlugin = new NotifyingSettingsProcessor(settingsProcessor, buildOperationExecutor)
        def classLoaderScope = Mock(ClassLoaderScope)
        def startParameter = Mock(StartParameter)

        when:
        buildOperationScriptPlugin.process(gradleInternal, settingsLocation, classLoaderScope, startParameter)

        then:
//        2 * scriptSource.getResource() >> scriptSourceResource
//        1 * scriptSourceResource.getLocation() >> scriptSourceResourceLocation
//        1 * scriptSourceResource.isContentCached() >> true
//        1 * scriptSourceResource.getHasEmptyContent() >> false
//        2 * decoratedScriptPlugin.getSource() >> scriptSource
//        1 * scriptSource.getDisplayName() >> "test.source"
//        1 * decoratedScriptPlugin.apply(target)
//        0 * decoratedScriptPlugin._

        buildOperationExecutor.operations.size() == 1
        buildOperationExecutor.operations.get(0).displayName == "Apply script test.source to Test Target"
        buildOperationExecutor.operations.get(0).name == "Apply script test.source"
    }
}
