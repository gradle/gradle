/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.resource.ResourceLocation
import org.gradle.internal.resource.TextResource
import spock.lang.Specification

class DefaultInitScriptProcessorTest extends Specification {

    void "can execute init script"() {
        when:
        def gradleMock = Mock(GradleInternal)
        def uri = new URI("file:///foo")
        def initScriptMock = Stub(ScriptSource) {
            getResource() >> Stub(TextResource) {
                getLocation() >> Stub(ResourceLocation) {
                    getURI() >> uri
                }
            }
        }
        def siblingScope = Mock(ClassLoaderScope)
        def gradleScope = Mock(ClassLoaderScope)
        def scriptApplicator = Mock(ScriptApplicator)

        1 * gradleMock.getClassLoaderScope() >> gradleScope
        1 * gradleScope.createChild("init-$uri") >> siblingScope
        1 * scriptApplicator.applyTo(gradleMock, initScriptMock, null, siblingScope, gradleScope, true)

        def processor = new DefaultInitScriptProcessor(scriptApplicator)

        then:
        processor.process(initScriptMock, gradleMock)
    }
}
