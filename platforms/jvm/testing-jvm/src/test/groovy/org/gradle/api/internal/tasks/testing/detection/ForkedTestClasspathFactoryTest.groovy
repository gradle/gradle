/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.util.GradleVersion
import spock.lang.Specification

/**
 * Tests {@link ForkedTestClasspathFactory}.
 */
class ForkedTestClasspathFactoryTest extends Specification {

    ForkedTestClasspathFactory underTest = new ForkedTestClasspathFactory(new DefaultModuleRegistry(
        CurrentGradleInstallation.locate().get()
    ))

    def "contains expected jars"() {
        given:
        def gradleDependencies = [
            "base-asm",
            "base-services",
            "build-operations",
            "build-option",
            "classloaders",
            "cli",
            "concurrent",
            "enterprise-logging",
            "enterprise-operations",
            "enterprise-workers",
            "file-temp",
            "files",
            "functional",
            "hashing",
            "internal-instrumentation-api",
            "io",
            "logging",
            "logging-api",
            "messaging",
            "native",
            "problems-api",
            "process-memory-services",
            "serialization",
            "service-lookup",
            "service-provider",
            "service-registry-builder",
            "service-registry-impl",
            "snapshots",
            "stdlib-java-extensions",
            "testing-base-infrastructure",
            "testing-jvm-infrastructure",
            "time",
            "worker-main",
        ]

        def externalDependencies = [
            "asm",
            "asm-tree",
            "commons-io",
            "commons-lang3",
            "error_prone_annotations",
            "failureaccess",
            "fastutil-8.5.2",
            "gradle-fileevents",
            "groovy",
            "gson",
            "guava-33.4.6",
            "jansi",
            "javax.inject",
            "jcl-over-slf4j",
            "jspecify-1.0.0-no-module",
            "jsr305",
            "jul-to-slf4j",
            "kryo",
            "log4j-over-slf4j",
            "minlog",
            "native-platform",
            "native-platform-freebsd-amd64-libcpp",
            "native-platform-linux-aarch64",
            "native-platform-linux-aarch64-ncurses5",
            "native-platform-linux-aarch64-ncurses6",
            "native-platform-linux-amd64",
            "native-platform-linux-amd64-ncurses5",
            "native-platform-linux-amd64-ncurses6",
            "native-platform-osx-aarch64",
            "native-platform-osx-amd64",
            "native-platform-windows-amd64",
            "native-platform-windows-amd64-min",
            "native-platform-windows-i386",
            "native-platform-windows-i386-min",
            "objenesis",
            "slf4j-api",
        ]

        when:
        def classpath = underTest.create().getAsFiles()

        then:
        def mutableClasspath = new ArrayList<File>(classpath)
        for (String dep : gradleDependencies) {
            List<File> matches = mutableClasspath.findAll { it.name.startsWith("gradle-${dep}-${GradleVersion.current().baseVersion.version}") }
            assert matches.size() > 0 : "Expected to find at least one match for gradle dependency '${dep}', but found none"
            def shortestMatch = matches.stream().min(Comparator.<File>comparingInt(x -> x.getName().length())).get()
            mutableClasspath.remove(shortestMatch)
        }

        for (String dep : externalDependencies) {
            List<File> matches = mutableClasspath.findAll { it.name.startsWith(dep) }
            assert matches.size() > 0 : "Expected to find at least one match for external dependency '${dep}', but found none"
            def shortestMatch = matches.stream().min(Comparator.<File>comparingInt(x -> x.getName().length())).get()
            mutableClasspath.remove(shortestMatch)
        }

        mutableClasspath.size() == 0
    }

}
