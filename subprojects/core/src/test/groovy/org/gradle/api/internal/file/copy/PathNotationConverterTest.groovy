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

package org.gradle.api.internal.file.copy

import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.UnsupportedNotationException

import java.util.concurrent.Callable
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import spock.lang.Specification

class PathNotationConverterTest extends Specification {
    NotationParser<Object, String> pathNotationParser = PathNotationConverter.parser();

    def "with null"() {
        expect:
        null == pathNotationParser.parseNotation(null);
    }

    def "with CharSequence"() {
        expect:
        pathToParse == pathNotationParser.parseNotation(pathToParse);
        where:
        pathToParse << ["this/is/a/path", 'this/is/a/path', "this/is/a/${'path'}"]
    }

    def "with File"() {
        def file = new File("a.txt")

        expect:
        pathNotationParser.parseNotation(file) == file.path
    }

    def "with Number"() {
        expect:
        expected == pathNotationParser.parseNotation(input);
        where:
        input << [1, 1.5, -1]
        expected << ["1", "1.5", "-1"]
    }

    def "with Boolean"() {
        expect:
        expected == pathNotationParser.parseNotation(input);
        where:
        input << [true, false, Boolean.TRUE, Boolean.FALSE]
        expected << ["true", "false", "true", "false"]
    }

    def "with unsupported class"() {
        def customObj = new DefaultExcludeRule();

        when:
        pathNotationParser.parseNotation(customObj);

        then:
        thrown(UnsupportedNotationException)
    }

    def "with closure "() {
        expect:
        "closure/path" == pathNotationParser.parseNotation({ "closure/path"});
    }

    def "with double nested closure "() {
        expect:
        "closure/path" == pathNotationParser.parseNotation({ "closure/path" });
    }

    def "with closure with null return value"() {
        expect:
        null == pathNotationParser.parseNotation({ null });
    }

    def "with Callable of unsupported return value"() {
        def customObj = new DefaultExcludeRule()

        when:
        pathNotationParser.parseNotation({ customObj } as Callable);

        then:
        thrown(UnsupportedNotationException)
    }

    def "with closure of unsupported return value"() {
        def customObj = new DefaultExcludeRule()

        when:
        pathNotationParser.parseNotation({ customObj });

        then:
        thrown(UnsupportedNotationException)
    }

    def "with Provider of String"() {
        DefaultProviderFactory providerFactory = new DefaultProviderFactory();
        def provider = providerFactory.provider(new Callable<String>() {
            String call() {
                return "provider/path";
            }
        })
        expect:
        "provider/path" == pathNotationParser.parseNotation(provider);
    }

    def "with Callable that throws exception"() {
        Callable callable = new Callable<String>() {
            String call() {
                return "callable/path";
            }
        };
        expect:
        "callable/path" == pathNotationParser.parseNotation(callable);
    }
}
