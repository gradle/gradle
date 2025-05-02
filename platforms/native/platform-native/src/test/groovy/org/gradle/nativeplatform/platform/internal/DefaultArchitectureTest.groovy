/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.platform.internal

import spock.lang.Specification

class DefaultArchitectureTest extends Specification {
    def "has useful string representation"() {
        def architecture = new DefaultArchitecture("arch")

        expect:
        architecture.toString() == "architecture 'arch'"
        architecture.displayName == "architecture 'arch'"
    }

    def "recognises key architectures"() {
        def arch = new DefaultArchitecture(name)

        expect:
        arch.name == name
        arch.i386 == i386
        arch.amd64 == amd64
        arch.ia64 == ia64
        arch.arm == arm || arm64
        arch.arm32 == arm
        arch.arm64 == arm64

        where:
        name        | i386  | amd64 | ia64  | arm   | arm64
        "x86"       | true  | false | false | false | false
        "x86-64"    | false | true  | false | false | false
        "ia-64"     | false | false | true  | false | false
        "arm-v7"    | false | false | false | true  | false
        "aarch64"   | false | false | false | false | true
        "arbitrary" | false | false | false | false | false
    }

    def "can create arbitrary operating system"() {
        def arch = new DefaultArchitecture("arbitrary")

        expect:
        arch.name == "arbitrary"
        arch.toString() == "architecture 'arbitrary'"
    }
}
