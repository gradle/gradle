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

package org.gradle.configuration

import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.Describables
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resource.ResourceLocation
import org.gradle.internal.resource.TextResource
import spock.lang.Specification

class BuildOperationScriptPluginTest extends Specification {

    def buildOperationExecutor = new TestBuildOperationExecutor()
    def userCodeApplicationContext = new DefaultUserCodeApplicationContext()
    def scriptSource = Mock(ScriptSource)
    def scriptSourceResource = Mock(TextResource)
    def scriptSourceResourceLocation = Mock(ResourceLocation)
    def decoratedScriptPlugin = Mock(ScriptPlugin)
    def buildOperationScriptPlugin = new BuildOperationScriptPlugin(decoratedScriptPlugin, buildOperationExecutor, userCodeApplicationContext)
    def target = "Test Target"

    def "delegates to decorated script plugin via build operation"() {
        when:
        buildOperationScriptPlugin.apply(target)

        then:
        2 * scriptSource.getResource() >> scriptSourceResource
        1 * scriptSourceResource.getLocation() >> scriptSourceResourceLocation
        1 * scriptSourceResource.isContentCached() >> true
        1 * scriptSourceResource.getHasEmptyContent() >> false
        3 * decoratedScriptPlugin.getSource() >> scriptSource
        2 * scriptSource.shortDisplayName >> Describables.of("<test.source>")
        1 * decoratedScriptPlugin.apply(target)
        0 * decoratedScriptPlugin._

        buildOperationExecutor.operations.size() == 1
        buildOperationExecutor.operations.get(0).displayName == "Apply <test.source> to $target"
        buildOperationExecutor.operations.get(0).name == "Apply <test.source>"
    }

    def "delegates to decorated script plugin without build operation in cached source has no content"() {
        when:
        buildOperationScriptPlugin.apply(target)

        then:
        1 * scriptSource.getResource() >> scriptSourceResource
        1 * scriptSourceResource.isContentCached() >> true
        1 * scriptSourceResource.getHasEmptyContent() >> true
        1 * decoratedScriptPlugin.getSource() >> scriptSource
        1 * decoratedScriptPlugin.apply(target)
        0 * decoratedScriptPlugin._

        buildOperationExecutor.operations.size() == 0
    }
}
