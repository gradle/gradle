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

import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultNativePlatformTest extends Specification {
    def os = Mock(OperatingSystemInternal)
    def arch = Mock(ArchitectureInternal)
    def platform = new DefaultNativePlatform("platform", os, arch)

    def "has useful string representation"() {
        expect:
        platform.displayName == "platform 'platform'"
        platform.toString() == "platform 'platform'"
    }

    def "can configure architecture"() {
        when:
        platform.architecture "x86"

        then:
        platform.architecture.name == "x86"
        platform.architecture.i386

        when:
        platform.architecture "i386"

        then:
        platform.architecture.name == "x86"
        platform.architecture.i386
    }

    def "can configure operating system"() {
        when:
        platform.operatingSystem "the-os"

        then:
        platform.operatingSystem.name == "the-os"
    }

    def "host platform has useful display name"() {
        expect:
        def host = DefaultNativePlatform.host()
        host.displayName.startsWith("host operating system")
        host.toString() == host.displayName

        def host2 = host.withArchitecture(arch)
        host2.displayName.startsWith("host operating system")
        host2.toString() == host2.displayName
    }
}
