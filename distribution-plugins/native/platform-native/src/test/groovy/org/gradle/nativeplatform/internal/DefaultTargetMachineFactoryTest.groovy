/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.internal

import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultTargetMachineFactoryTest extends Specification {
    def factory = new DefaultTargetMachineFactory(TestUtil.objectFactory())

    def "can use created target machines in Set"() {
        def windows1 = factory.windows
        def windows2 = factory.windows
        def linux1 = factory.linux.x86_64
        def linux2 = factory.linux.x86_64

        expect:
        def targetMachines = [windows1, windows2, linux1, linux2] as Set
        targetMachines.size() == 2
        targetMachines == [windows1, linux1] as Set
    }

    def "same target machine are equals"() {
        expect:
        factory.windows == factory.windows
        factory.linux == factory.linux
        factory.macOS == factory.macOS
        factory.windows.x86 == factory.windows.x86
        factory.linux.x86_64 == factory.linux.x86_64
        factory.windows.architecture("arm") == factory.windows.architecture("arm")
        factory.os("fushia").architecture("arm") == factory.os("fushia").architecture("arm")
    }

    def "different target machine are not equals"() {
        expect:
        factory.windows != factory.linux
        factory.linux != factory.macOS
        factory.macOS != factory.windows
        factory.windows.x86 != factory.windows.x86_64
        factory.linux.x86 != factory.windows.x86
        factory.windows.architecture("arm") != factory.windows.architecture("leg")
        factory.os("fushia").architecture("arm") != factory.os("helenOS").architecture("arm")
    }
}
