/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.logging

import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Specification

class ConsoleRendererTest extends Specification {
    ConsoleRenderer renderer = new ConsoleRenderer()

    @Requires(UnitTestPreconditions.NotWindows)
    def "produces triple-slash file URLs"() {
        expect:
        renderer.asClickableFileUrl(new File("/foo/bar/baz")) == "file:///foo/bar/baz"
    }

    @Requires(UnitTestPreconditions.Windows)
    def "produces triple-slash file URLs on Windows"() {
        expect:
        renderer.asClickableFileUrl(new File("C:\\foo\\bar\\baz")) == "file:///C:/foo/bar/baz"
    }
}
