/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.cli

import spock.lang.Specification

class AbstractPropertiesCommandLineConverterTest extends Specification {

    final AbstractPropertiesCommandLineConverter converter = new AbstractPropertiesCommandLineConverter(){
        @Override
        protected String getPropertyOption() {
            return "A"
        }

        @Override
        protected String getPropertyOptionDetailed() {
            return "another-prop"
        }

        @Override
        protected String getPropertyOptionDescription() {
            return "Test Description of a test property based option"
        }
    }

    def convert(String... args) {
        converter.convert(Arrays.asList(args), new HashMap<String, String>()).sort()
    }

    def "configures property based options"() {
        def parser = Mock(CommandLineParser);
        def option = Mock(CommandLineOption);
        setup:
            parser.option(_,_) >> option
            option.hasArguments() >> option
            option.hasDescription(_) >> option
        when:
            converter.configure(parser)
        then:
            1 * option.hasArguments() >> option
            1 * option.hasDescription(converter.propertyOptionDescription) >> option
            1 * parser.option(converter.propertyOption, converter.propertyOptionDetailed) >> option
    }

    def "parses properties args"() {
        expect:
        convert("-Aa=b", "-Ac=d") == [a: "b", c: "d"]
    }

    def "parses properties args with no property value"() {
        expect:
        convert("-Aa", "-Ab=") == [a: "", b: ""]
    }

    def "parses properties arg containing equals"() {
        expect:
        convert("-Aprop=a b=c", "-Aprop2==", "-Aprop3=ab=") == [prop: 'a b=c', prop2: '=', prop3: 'ab=']
    }
}