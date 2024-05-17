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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.api.Action
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalBuildController
import spock.lang.Specification

class BuildControllerWithoutParameterSupportTest extends Specification {
    def version = Mock(VersionDetails)
    def delegate = Mock(InternalBuildController)
    def modelMapping = new ModelMapping()
    def controller = new BuildControllerWithoutParameterSupport(delegate, new ProtocolToModelAdapter(), modelMapping, new File("root dir"), version)

    def "delegates when no parameter is given"() {
        def model = new Object()
        def modelType = Object
        def buildResult = Stub(BuildResult) {
            getModel() >> model
        }

        when:
        def result = controller.getModel(null, modelType, null, null)

        then:
        1 * delegate.getModel(null, {it.name == Object.name}) >> buildResult
        0 * delegate._

        and:
        result == model
    }

    def "throws exception when parameter is given"() {
        def modelType = Object.class
        def parameterType = Object.class
        def parameterInitializer = Mock(Action)

        given:
        _ * version.getVersion() >> "4.1"

        when:
        controller.getModel(null, modelType, parameterType, parameterInitializer)

        then:
        UnsupportedVersionException e = thrown()
        e.message == "Gradle version 4.1 does not support parameterized tooling models."
    }
}
