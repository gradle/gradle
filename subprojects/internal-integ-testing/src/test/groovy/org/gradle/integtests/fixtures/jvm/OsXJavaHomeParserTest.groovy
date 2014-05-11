/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.integtests.fixtures.jvm

import org.gradle.api.JavaVersion
import org.gradle.util.VersionNumber
import spock.lang.Specification

class OsXJavaHomeParserTest extends Specification {
    def parser = new OsXJavaHomeParser()

    def "parses new format output"() {
        def output = """Matching Java Virtual Machines (10):
    1.8.0, x86_64:\t"Java SE 8"\t/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home
    1.7.0_17, x86_64:\t"Java SE 7"\t/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home
    1.7.0_07, x86_64:\t"Java SE 7"\t/Library/Java/JavaVirtualMachines/jdk1.7.0_07.jdk/Contents/Home
    1.7.0_06, x86_64:\t"Java SE 7"\t/Library/Java/JavaVirtualMachines/jdk1.7.0_06.jdk/Contents/Home
    1.7.0-ea-b223-ea-b223-ea-b223, x86_64:\t"Java SE 7 Developer Preview"\t/Library/Java/JavaVirtualMachines/JDK 1.7.0 Developer Preview.jdk/Contents/Home
    1.7.0-ea-b223-ea-b223-ea-b223, i386:\t"Java SE 7 Developer Preview"\t/Library/Java/JavaVirtualMachines/JDK 1.7.0 Developer Preview.jdk/Contents/Home
    1.7.0, x86_64:\t"OpenJDK 7"\t/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home
    1.7.0, i386:\t"OpenJDK 7"\t/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home
    1.6.0_65-b14-462, x86_64:\t"Java SE 6"\t/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
    1.6.0_65-b14-462, i386:\t"Java SE 6"\t/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home

"""

        expect:
        def result = parser.parse(new StringReader(output))
        result.size() == 10
        result[0].version == VersionNumber.parse("1.8.0")
        result[0].javaVersion == JavaVersion.VERSION_1_8
        result[0].jdk
        result[0].arch == JvmInstallation.Arch.x86_64
        result[0].javaHome == new File("/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home").canonicalFile

        result[1].version == VersionNumber.parse("1.7.0_17")
        result[1].javaVersion == JavaVersion.VERSION_1_7
        result[1].javaHome == new File("/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home").canonicalFile

        result[4].version == VersionNumber.parse("1.7.0-ea-b223-ea-b223-ea-b223")
        result[4].javaVersion == JavaVersion.VERSION_1_7
        result[4].javaHome == new File("/Library/Java/JavaVirtualMachines/JDK 1.7.0 Developer Preview.jdk/Contents/Home").canonicalFile

        result[6].version == VersionNumber.parse("1.7.0")
        result[6].javaVersion == JavaVersion.VERSION_1_7
        result[6].javaHome == new File("/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home").canonicalFile

        result[8].version == VersionNumber.parse("1.6.0_65-b14-462")
        result[8].javaVersion == JavaVersion.VERSION_1_6
        result[8].jdk
        result[8].arch == JvmInstallation.Arch.x86_64
        result[8].javaHome == new File("/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home").canonicalFile

        result[9].version == VersionNumber.parse("1.6.0_65-b14-462")
        result[9].javaVersion == JavaVersion.VERSION_1_6
        result[9].jdk
        result[9].arch == JvmInstallation.Arch.i386
        result[9].javaHome == new File("/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home").canonicalFile
    }

    def "parses old format output"() {
        def output ="""Matching Java Virtual Machines (2):
    1.6.0_17 (x86_64):\t/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home
    1.6.0_17 (i386):\t/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home

"""

        expect:
        def result = parser.parse(new StringReader(output))
        result.size() == 2
        result[0].version == VersionNumber.parse("1.6.0_17")
        result[0].javaVersion == JavaVersion.VERSION_1_6
        result[0].jdk
        result[0].arch == JvmInstallation.Arch.x86_64
        result[0].javaHome == new File("/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home").canonicalFile

        result[1].version == VersionNumber.parse("1.6.0_17")
        result[1].javaVersion == JavaVersion.VERSION_1_6
        result[1].jdk
        result[1].arch == JvmInstallation.Arch.i386
        result[1].javaHome == new File("/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home").canonicalFile
    }
}
