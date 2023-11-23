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

package org.gradle.nativeplatform.platform.internal

import spock.lang.Specification


class ArchitecturesTest extends Specification {
    def "test 32-bit aliases"() {
        expect:
        Architectures.forInput(architecture).isI386()
        where:
        architecture << [ "x86", "i386", "ia-32", "i686" ]
    }

    def "test 64-bit aliases"() {
        expect:
        Architectures.forInput(architecture).isAmd64()
        where:
        architecture << [ "x86-64", "x86_64", "amd64", "x64" ]
    }

    def "test ARM aliases"() {
        expect:
        Architectures.forInput(architecture).isArm32()
        Architectures.forInput(architecture).isArm()
        where:
        architecture << [ "arm", "armv7" ]
    }

    def "test ARM 64 aliases"() {
        expect:
        Architectures.forInput(architecture).isArm64()
        Architectures.forInput(architecture).isArm()
        where:
        architecture << [ "aarch64", "arm-v8" ]
    }
}
