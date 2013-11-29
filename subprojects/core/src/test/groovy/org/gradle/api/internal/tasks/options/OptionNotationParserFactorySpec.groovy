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

package org.gradle.api.internal.tasks.options

import org.gradle.api.GradleException
import spock.lang.Specification

class OptionNotationParserFactorySpec extends Specification {

    def "creates notationparser for handling strings"(){
        given:
        OptionNotationParserFactory factory = new OptionNotationParserFactory()
        when:
        def parser = factory.toComposite(String.class);
        then:
        parser.parseNotation("somestring") == "somestring"
    }

    def "creates notationparser for handling handles enums"(){
        given:
        OptionNotationParserFactory factory = new OptionNotationParserFactory()
        when:
        def parser = factory.toComposite(TestEnum.class);
        then:
        parser.parseNotation(TestEnum.ABC) == TestEnum.ABC
        parser.parseNotation("ABC") == TestEnum.ABC
    }

    def "fails on creating parser for unsupported"(){
        setup:
        OptionNotationParserFactory factory = new OptionNotationParserFactory()
        when:
        factory.toComposite(File.class);
        then:
        def e = thrown(GradleException);
        e.message == "resultingType 'java.io.File' not supported"
    }

    enum TestEnum {
        ABC, DEF
    }
}
