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

package org.gradle.internal.buildoption


import org.gradle.cli.CommandLineParser
import spock.lang.Specification

class BuildOptionSetTest extends Specification {
    def set = new OptionSet()

    def "can parse command-line options"() {
        def parser = new CommandLineParser()
        def converter = set.commandLineConverter()

        when:
        converter.configure(parser)
        def args = parser.parse("--some-option", "abc", "--some-flag")
        def bean = converter.convert(args, new Bean())

        then:
        bean.flag
        bean.prop == "abc"
    }

    def "can convert properties"() {
        def converter = set.propertiesConverter()

        when:
        def bean = converter.convert(["some.option": "abc", "some.flag": "true"], new Bean())

        then:
        bean.flag
        bean.prop == "abc"
    }

    private class Bean {
        boolean flag
        String prop
    }

    private class OptionSet extends BuildOptionSet<Bean> {
        @Override
        List<? extends BuildOption<? super Bean>> getAllOptions() {
            return [new TestOption(), new TestFlag()]
        }
    }

    private class TestOption extends StringBuildOption<Bean> {
        TestOption() {
            super("some.option", CommandLineOptionConfiguration.create("some-option", "an option"))
        }

        @Override
        void applyTo(String value, Bean settings, Origin origin) {
            settings.prop = value
        }
    }

    private class TestFlag extends BooleanBuildOption<Bean> {
        TestFlag() {
            super("some.flag", BooleanCommandLineOptionConfiguration.create("some-flag", "thing is on", "this is off"))
        }

        @Override
        void applyTo(boolean value, Bean settings, Origin origin) {
            settings.flag = value
        }
    }
}
