/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal

import org.gradle.internal.logging.text.TestStyledTextOutput
import spock.lang.Specification

import static org.gradle.util.Matchers.containsLine

class PropertyReportRendererTest extends Specification {
    private final TestStyledTextOutput out = new TestStyledTextOutput()
    private final PropertyReportRenderer renderer = new PropertyReportRenderer(){{
        setOutput(out)
    }}

    def 'writes property'() {
        when:
        renderer.addProperty("prop", "value")

        then:
        assert containsLine(out.toString(), "prop: value")
    }

    def 'writes null property'() {
        when:
        renderer.addProperty("prop", null)

        then:
        assert containsLine(out.toString(), "prop: null")
    }

    void 'writes property that throws in toString'() {
        when:
        renderer.addProperty("prop", new RenderFailedValue())

        then:
        assert containsLine(out.toString(), 'prop: ' +
            'class org.gradle.api.tasks.diagnostics.internal.PropertyReportRendererTest$RenderFailedValue ' +
            '[Rendering failed]')
    }

    private static class RenderFailedValue {
        @Override
        String toString() {
            throw new UnsupportedOperationException("You cannot toString me!")
        }
    }
}
