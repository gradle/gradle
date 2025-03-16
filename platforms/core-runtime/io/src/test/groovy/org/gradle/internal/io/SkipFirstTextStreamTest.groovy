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

package org.gradle.internal.io

import spock.lang.Specification

class SkipFirstTextStreamTest extends Specification {

    def "skips the first emission"() {
        given:
        def delegate = Mock(TextStream)
        def skipper = new SkipFirstTextStream(delegate)

        when:
        skipper.text("a")
        skipper.text("b")
        skipper.endOfStream(null)

        then:
        1 * delegate.text("b")
        0 * delegate.text(_)

        and:
        1 * delegate.endOfStream(null)
    }

    def "does not skip EOS if immediately after first"() {
        given:
        def delegate = Mock(TextStream)
        def skipper = new SkipFirstTextStream(delegate)

        when:
        skipper.text("a")
        skipper.endOfStream(null)

        then:
        1 * delegate.endOfStream(null)
        0 * delegate._
    }

    def "does not skip EOS if before first"() {
        given:
        def delegate = Mock(TextStream)
        def skipper = new SkipFirstTextStream(delegate)

        when:
        skipper.endOfStream(null)

        then:
        1 * delegate.endOfStream(null)
        0 * delegate._
    }
}
