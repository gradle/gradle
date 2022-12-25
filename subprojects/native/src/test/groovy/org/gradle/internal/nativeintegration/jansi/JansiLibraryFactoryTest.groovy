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

package org.gradle.internal.nativeintegration.jansi

import spock.lang.Specification

import static org.gradle.internal.nativeintegration.jansi.JansiLibraryFactory.*

class JansiLibraryFactoryTest extends Specification {

    def factory = new JansiLibraryFactory()
    def resolver = Mock(JansiRuntimeResolver)

    def setup() {
        factory.jansiRuntimeResolver = resolver
    }

    def "jansi library can be created for MacOSX"() {
        when:
        JansiLibrary jansiLibrary = factory.create()

        then:
        1 * resolver.operatingSystem >> JansiOperatingSystemSupport.MAC_OS_X.identifier
        0 * resolver.platform
        jansiLibrary.platform == JansiOperatingSystemSupport.MAC_OS_X.identifier
        jansiLibrary.filename == MAC_OSX_LIB_FILENAME
        jansiLibrary.resourcePath ==  "/META-INF/native/" + jansiLibrary.path
    }

    def "jansi library can be created for Linux platform #platform"() {
        when:
        JansiLibrary jansiLibrary = factory.create()

        then:
        1 * resolver.operatingSystem >> JansiOperatingSystemSupport.LINUX.identifier
        1 * resolver.platform >> platform
        jansiLibrary.platform == platform
        jansiLibrary.filename == LINUX_LIB_FILENAME
        jansiLibrary.resourcePath ==  "/META-INF/native/" + jansiLibrary.path

        where:
        platform << ['linux32', 'linux64']
    }

    def "jansi library can be created for Windows platform #platform"() {
        when:
        JansiLibrary jansiLibrary = factory.create()

        then:
        1 * resolver.operatingSystem >> JansiOperatingSystemSupport.WINDOWS.identifier
        1 * resolver.platform >> platform
        jansiLibrary.platform == platform
        jansiLibrary.filename == WINDOWS_LIB_FILENAME
        jansiLibrary.resourcePath ==  "/META-INF/native/" + jansiLibrary.path

        where:
        platform << ['windows32', 'windows64']
    }

    def "jansi library cannot be created for unsupported OS"() {
        when:
        JansiLibrary jansiLibrary = factory.create()

        then:
        1 * resolver.operatingSystem >> 'unknown'
        0 * resolver.platform
        !jansiLibrary
    }
}
