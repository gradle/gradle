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

package org.gradle.launcher.cli.converter

import org.gradle.api.internal.file.TestFiles
import org.gradle.cli.CommandLineParser
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonParameters
import spock.lang.Specification

class DaemonCommandLineConverterTest extends Specification {
    def "converts daemon options - #options"() {
        when:
        def converted = convert(options)

        then:
        converted.enabled == useDaemon

        where:
        options                         | useDaemon
        []                              | true
        ['--no-daemon']                 | false
        ['--foreground', '--no-daemon'] | false
        ['--no-daemon', '--foreground'] | false
        ['--daemon']                    | true
        ['--no-daemon', '--daemon']     | true
    }

    def "can convert foreground option - #options"() {
        when:
        def converted = convert(options)

        then:
        converted.foreground == foreground

        where:
        options                         | foreground
        []                              | false
        ['--foreground']                | true
        ['--foreground', '--no-daemon'] | true
        ['--foreground', '--daemon']    | true
    }

    def "can convert stop option - #options"() {
        when:
        def converted = convert(options)

        then:
        converted.stop == stop

        where:
        options    | stop
        []         | false
        ['--stop'] | true
    }

    def "can convert status option - #options"() {
        when:
        def converted = convert(options)

        then:
        converted.status == status

        where:
        options      | status
        []           | false
        ['--status'] | true
    }

    private DaemonParameters convert(Iterable args) {
        CommandLineParser parser = new CommandLineParser()
        def converter = new DaemonBuildOptions().commandLineConverter()
        converter.configure(parser)
        converter.convert(args, new DaemonParameters(new File("gradle-user-home"), TestFiles.fileCollectionFactory()))
    }
}
