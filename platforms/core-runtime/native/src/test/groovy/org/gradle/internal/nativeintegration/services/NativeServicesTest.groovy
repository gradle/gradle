/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration.services

import net.rubygrapefruit.platform.ProcessLauncher
import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.file.Chmod
import org.gradle.internal.file.FileCanonicalizer
import org.gradle.internal.file.Stat
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.nativeintegration.console.ConsoleDetector
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class NativeServicesTest extends Specification {
    NativeServices services

    def setup() {
        services = NativeServices.getInstance()
    }

    def "makes a ProcessEnvironment available"() {
        expect:
        services.get(ProcessEnvironment) != null
    }

    def "makes an OperatingSystem available"() {
        expect:
        services.get(OperatingSystem) != null
    }

    def "makes a FileSystem available"() {
        expect:
        services.get(FileSystem) != null
        services.get(Chmod) != null
        services.get(Stat) != null
    }

    def "makes a FileCanonicalizer available"() {
        expect:
        services.get(FileCanonicalizer) != null
    }

    def "makes a ConsoleDetector available"() {
        expect:
        services.get(ConsoleDetector) != null
    }

    def "makes a WindowsRegistry available"() {
        expect:
        services.get(WindowsRegistry) != null
    }

    def "makes a SystemInfo available"() {
        expect:
        services.get(SystemInfo) != null
    }

    def "makes a ProcessLauncher available"() {
        expect:
        services.get(ProcessLauncher) != null
    }

    def "makes a TemporaryFileProvider available"() {
        expect:
        services.get(TemporaryFileProvider) != null
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "try using a WindowsRegistry on a non-Windows OS"() {
        def service = services.get(WindowsRegistry)
        when:
        service.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\AdoptOpenJDK\\JDK")
        then:
        def e = thrown(NativeIntegrationUnavailableException)
        e.message == "Service 'WindowsRegistry' is not available (os=${OperatingSystem.current()}, enabled=true)."
    }
}
