/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs

import org.gradle.util.TextUtil
import spock.lang.Specification

class UserGuideTransformTaskTest extends Specification {

    def replacesTabsWith4Spaces() {
        given:
        String content = "test\ttest\ttest"
        when:
        def actual = UserGuideTransformTask.normalise(content)

        then:
        actual == "test    test    test"
    }

    def usesUnixLineEndings() {
        given:
        String content = "test\r\ntest\r\ntest"
        when:
        def actual = UserGuideTransformTask.normalise(content)

        then:
        actual == "test\ntest\ntest"
    }

    def stripsLeadingWhitespaceOnSingleLine() {
        given:
        String content = "     test"
        when:
        def actual = UserGuideTransformTask.normalise(content)

        then:
        actual == "test"
    }

    def preservesFormattingWithIndentedMultiline() {
        given:
        String content = TextUtil.normaliseLineSeparators(
"""            sources {
                cpp {
                    lib library: "hello"
                }
            }""")
        when:
        def actual = UserGuideTransformTask.normalise(content)

        then:
        actual ==
"""sources {
    cpp {
        lib library: "hello"
    }
}"""
    }
}
