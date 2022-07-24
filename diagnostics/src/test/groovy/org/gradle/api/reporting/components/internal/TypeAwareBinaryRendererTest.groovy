/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.components.internal

import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.platform.base.BinarySpec
import spock.lang.Specification

class TypeAwareBinaryRendererTest extends Specification {
    def "delegates to renderer for type"() {
        def binary = Stub(SomeBinary)
        def output = Stub(TextReportBuilder)
        def renderer = new TypeAwareBinaryRenderer()
        def binRenderer = Mock(AbstractBinaryRenderer)

        given:
        binRenderer.targetType >> binary.getClass()
        renderer.register(binRenderer)

        when:
        renderer.render(binary, output)

        then:
        1 * binRenderer.render(binary, output)
    }

    def "delegates to renderer for most specific super type"() {
        def binary = Stub(SomeBinary)
        def output = Stub(TextReportBuilder)
        def renderer = new TypeAwareBinaryRenderer()
        def binRenderer1 = Mock(AbstractBinaryRenderer)
        def binRenderer2 = Mock(AbstractBinaryRenderer)

        given:
        binRenderer1.targetType >> BinarySpec
        binRenderer2.targetType >> SomeSpecializedBinary
        renderer.register(binRenderer1)
        renderer.register(binRenderer2)

        when:
        renderer.render(binary, output)

        then:
        1 * binRenderer1.render(binary, output)
        0 * binRenderer2._
    }

    interface SomeBinary extends BinarySpec { }
    interface SomeSpecializedBinary extends SomeBinary { }
}
