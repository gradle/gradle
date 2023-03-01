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

package org.gradle.jvm.toolchain.internal

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification

class WindowsInstallationSupplierTest extends Specification {

    def registry = Mock(WindowsRegistry)

    def "no detection for non-windows os"() {
        given:
        def supplier = createSupplier(OperatingSystem.MAC_OS)

        when:
        supplier.get()

        then:
        0 * registry._
    }

    def "finds openjdk homes"() {
        given:
        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\AdoptOpenJDK\\JDK") >> [
            "8.0",
            "9.0-abc"
        ]
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\AdoptOpenJDK\\JDK\\8.0\\hotspot\\MSI", "Path") >> "c:\\jdk8"
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\AdoptOpenJDK\\JDK\\9.0-abc\\hotspot\\MSI", "Path") >> "d:\\jdk9"

        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\Eclipse Adoptium\\JDK") >> [
            "17.0.3-7",
            "11.0.15-10",
        ]
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\Eclipse Adoptium\\JDK\\17.0.3-7\\hotspot\\MSI", "Path") >> "c:\\jdk17"
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\Eclipse Adoptium\\JDK\\11.0.15-10\\hotspot\\MSI", "Path") >> "d:\\jdk11"

        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\Eclipse Foundation\\JDK") >> [
            "15.0-abc",
            "16.0-def",
        ]
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\Eclipse Foundation\\JDK\\15.0-abc\\hotspot\\MSI", "Path") >> "c:\\jdk15"
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\Eclipse Foundation\\JDK\\16.0-def\\hotspot\\MSI", "Path") >> "d:\\jdk16"

        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, _) >> { throw new MissingRegistryEntryException() }
        def supplier = createSupplier()

        when:
        def locations = supplier.get()

        then:
        locations*.location.path.containsAll("c:\\jdk8", "d:\\jdk9", "c:\\jdk17", "d:\\jdk11", "c:\\jdk15", "d:\\jdk16")
        locations*.source == ["Windows Registry"] * 6
    }

    def "handles absent adoptopenjdk keys"() {
        given:
        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, "SOFTWARE\\AdoptOpenJDK\\JDK") >> { throw new MissingRegistryEntryException() }
        def supplier = createSupplier(OperatingSystem.MAC_OS)

        when:
        def locations = supplier.get()

        then:
        locations.isEmpty()
    }

    def "finds java homes #home via #key"() {
        given:
        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, key) >> [
            "8.0",
            "9.0-abc"
        ]
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, key + "\\8.0", "JavaHome") >> home + "/8.0-home"
        registry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, key + "\\9.0-abc", "JavaHome") >> home + "/9.0-home"
        registry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, _) >> { throw new MissingRegistryEntryException() }
        def supplier = createSupplier()

        when:
        def locations = supplier.get()

        then:
        locations*.location.path.containsAll(home + "${File.separator}8.0-home", home + "${File.separator}9.0-home")
        locations*.source == ["Windows Registry", "Windows Registry"]

        where:
        key                                                         | home
        "SOFTWARE\\JavaSoft\\JDK"                                   | "c:\\javasoft-jdk"
        "SOFTWARE\\JavaSoft\\Java Development Kit"                  | "c:\\javasoft-jadevel"
        "SOFTWARE\\JavaSoft\\Java Runtime Environment"              | "c:\\javasoft-jre"
        "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit"     | "c:\\wow-jdk"
        "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment" | "c:\\wow-jre"
    }

    WindowsInstallationSupplier createSupplier(OperatingSystem os = OperatingSystem.WINDOWS) {
        new WindowsInstallationSupplier(registry, os, createProviderFactory())
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.notDefined()
        providerFactory
    }

}
