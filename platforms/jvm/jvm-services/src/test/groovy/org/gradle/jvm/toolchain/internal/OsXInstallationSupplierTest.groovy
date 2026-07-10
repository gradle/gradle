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


import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification

class OsXInstallationSupplierTest extends Specification {
    def osxJavaHomeCommand = Mock(OsXJavaHomeCommand)

    def "supplies no installations for absent output"() {
        given:
        def supplier = new OsXInstallationSupplier(OperatingSystem.MAC_OS, osxJavaHomeCommand)
        osxJavaHomeCommand.findJavaHomes() >> []

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }


    def "supplies no installations for wrong os"() {
        given:
        def supplier = new OsXInstallationSupplier(OperatingSystem.WINDOWS, osxJavaHomeCommand)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single candidate"() {
        given:
        def supplier = new OsXInstallationSupplier(OperatingSystem.MAC_OS, osxJavaHomeCommand)
        def expectedJavaHome = new File("/Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home")
        osxJavaHomeCommand.findJavaHomes() >> [expectedJavaHome]

        when:
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == expectedJavaHome
        directories[0].source == "MacOS java_home"
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        def supplier = new OsXInstallationSupplier(OperatingSystem.MAC_OS, osxJavaHomeCommand)
        def jdk7 = new File("/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home")
        def jdk8 = new File("/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home")
        def jdk9 = new File("/Library/Java/JavaVirtualMachines/jdk-9.jdk/Contents/Home")
        osxJavaHomeCommand.findJavaHomes() >> [jdk7, jdk8, jdk9]

        when:
        def directories = supplier.get()

        then:
        directories.size() == 3
        directories*.location.containsAll(jdk7, jdk8, jdk9)
        directories*.source.unique() == ["MacOS java_home"]
    }
}
