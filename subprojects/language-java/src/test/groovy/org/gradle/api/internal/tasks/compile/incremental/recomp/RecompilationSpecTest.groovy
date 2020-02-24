/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp

import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec.maybeClassName

class RecompilationSpecTest extends Specification {
    def "extracts a reasonable class name from a given String"() {
        expect:
        maybeClassName("com.example.Foo") == "com.example.Foo"
        maybeClassName("com.example.\$") == "com.example.\$"
        maybeClassName("com.example.\$Foo") == "com.example.\$Foo"
        maybeClassName("com.example.Foo\$Bar") == "com.example.Foo"
        maybeClassName("com.example.Foo\$") == "com.example.Foo"

        maybeClassName("Foo") == "Foo"
        maybeClassName("\$") == "\$"
        maybeClassName("\$Foo") == "\$Foo"
        maybeClassName("Foo\$Bar") == "Foo"
        maybeClassName("Foo\$") == "Foo"
    }
}
