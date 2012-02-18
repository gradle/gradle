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
package org.gradle.api.internal.tasks.compile

import spock.lang.Specification
import org.gradle.api.tasks.WorkResult
import org.gradle.api.JavaVersion

class CommandLineJavaCompilerSupportTest extends Specification {
    def compiler = new CommandLineJavaCompilerSupport() {
        WorkResult execute() { null }
    }
    def spec = compiler.spec

    def "generates no options unless configured"() {
        expect:
        compiler.generateCommandLineOptions() == []
    }

    def "generates no -source option when current Jvm Version is used"() {
        spec.sourceCompatibility = JavaVersion.current().toString();

        expect:
        compiler.generateCommandLineOptions() == []
    }

    def "generates -source option when compatibility differs current Jvm version"() {
        spec.sourceCompatibility = "1.4"

        expect:
        compiler.generateCommandLineOptions() == ["-source", "1.4"]
    }

    def "generates no -target option when current Jvm Version is used"() {
        compiler.spec.targetCompatibility = JavaVersion.current().toString();

        expect:
        compiler.generateCommandLineOptions() == []
    }

    def "generates -target option when compatibility differs current Jvm version"() {
        compiler.spec.targetCompatibility = "1.4"

        expect:
        compiler.generateCommandLineOptions() == ["-target", "1.4"]
    }

    def "generates -d option"() {
        def file = new File("/project/build")
        spec.destinationDir = file

        expect:
        compiler.generateCommandLineOptions() == ["-d", file.path]
    }

    def "generates -verbose option"() {
        when:
        spec.compileOptions.verbose = true

        then:
        compiler.generateCommandLineOptions() == ["-verbose"]

        when:
        spec.compileOptions.verbose = false

        then:
        compiler.generateCommandLineOptions() == []
    }

    def "generates -deprecation option"() {
        when:
        spec.compileOptions.deprecation = true

        then:
        compiler.generateCommandLineOptions() == ["-deprecation"]

        when:
        spec.compileOptions.deprecation = false

        then:
        compiler.generateCommandLineOptions() == []
    }

    def "generates -nowarn option"() {
        when:
        spec.compileOptions.warnings = true

        then:
        compiler.generateCommandLineOptions() == []

        when:
        spec.compileOptions.warnings = false

        then:
        compiler.generateCommandLineOptions() == ["-nowarn"]
    }

    def "generates -g:none option"() {
        when:
        spec.compileOptions.debug = true

        then:
        compiler.generateCommandLineOptions() == []

        when:
        spec.compileOptions.debug = false

        then:
        compiler.generateCommandLineOptions() == ["-g:none"]
    }

    def "generates -encoding option"() {
        when:
        spec.compileOptions.encoding = "some-encoding"

        then:
        compiler.generateCommandLineOptions() == ["-encoding", "some-encoding"]
    }

    def "generates -bootclasspath option"() {
        when:
        spec.compileOptions.bootClasspath = "/lib/lib1.jar:/lib/lib2.jar"

        then:
        compiler.generateCommandLineOptions() == ["-bootclasspath", "/lib/lib1.jar:/lib/lib2.jar"]
    }

    def "generates -extdirs option"() {
        when:
        spec.compileOptions.extensionDirs = "/dir1:/dir2"

        then:
        compiler.generateCommandLineOptions() == ["-extdirs", "/dir1:/dir2"]
    }

    def "generates -classpath option"() {
        def file1 = new File("/lib/lib1.jar")
        def file2 = new File("/lib/lib2.jar")
        spec.classpath = [file1, file2]

        expect:
        compiler.generateCommandLineOptions() == ["-classpath", "$file1$File.pathSeparator$file2"]
    }

    def "adds custom compiler args"() {
        spec.compileOptions.compilerArgs = ["-a", "value-a", "-b", "value-b"]

        expect:
        compiler.generateCommandLineOptions() == ["-a", "value-a", "-b", "value-b"]
    }
}
