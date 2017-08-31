/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.format

import org.gradle.internal.logging.events.StyledTextOutputEvent
import spock.lang.Specification
import spock.lang.Subject

class StatusPostfixLogHeaderFormatterTest extends Specification {
    @Subject statusPostfixLogHeaderFormatter = new StatusPostfixLogHeaderFormatter()
    private static final String HEADER = "HEADER"
    private static final String DESCRIPTION = "DESCRIPTION"
    private static final String SHORT_DESCRIPTION = "SHORT"

    def "does not render null status"() {
        when:
        def output = statusPostfixLogHeaderFormatter.format(null, DESCRIPTION, null, null, false)

        then:
        rendered(output) == """<Normal>DESCRIPTION</Normal><Normal>${LogHeaderFormatter.EOL}</Normal>"""
    }

    def "prefers header for rendering"() {
        given:
        def status = "STATUS"

        when:
        def output = statusPostfixLogHeaderFormatter.format(HEADER, DESCRIPTION, SHORT_DESCRIPTION, status, false)

        then:
        rendered(output) == """<Normal>HEADER </Normal><ProgressStatus>STATUS</ProgressStatus><Normal>${LogHeaderFormatter.EOL}</Normal>"""
    }

    def "prefers short description over description"() {
        when:
        def output = statusPostfixLogHeaderFormatter.format(null, DESCRIPTION, SHORT_DESCRIPTION, null, false)

        then:
        rendered(output) == """<Normal>SHORT</Normal><Normal>${LogHeaderFormatter.EOL}</Normal>"""
    }

    private static String rendered(List<StyledTextOutputEvent.Span> spans) {
        // Render and replace category and log level
        new StyledTextOutputEvent(0L, null, null, null, spans)
            .toString()
            .replaceAll('^\\[\\w+\\] \\[\\w+\\] ', '')
    }
}
