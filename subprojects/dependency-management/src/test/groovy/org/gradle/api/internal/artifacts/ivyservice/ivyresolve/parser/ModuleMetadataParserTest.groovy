/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.Transformer
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import spock.lang.Specification


class ModuleMetadataParserTest extends Specification {
    def parser = new ModuleMetadataParser()

    def "parses minimal metadata resource"() {
        expect:
        parser.parse(resource('{ "formatVersion": "0.1" }'))
    }

    def "parses content with variants"() {
        expect:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } },
        "variants": [
            {
                "attributes": { "usage": "compile" },
                "files": [ { "name": "a.zip", "url": "a.zop" } ]
            }
        ]
    }
'''))
    }

    def "fails on badly formed content"() {
        when:
        parser.parse(resource('not-json'))

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
    }

    def "fails when content does not contain a json object"() {
        when:
        parser.parse(resource('["abc"]'))

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == 'Expected BEGIN_OBJECT but was BEGIN_ARRAY at line 1 column 2 path $'
    }

    def "fails on missing format version"() {
        when:
        parser.parse(resource('{ }'))

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == "Module metadata should contain a 'formatVersion' value."

        when:
        parser.parse(resource('{ "other": 1.2 }'))

        then:
        def e2 = thrown(MetaDataParseException)
        e2.message == 'Could not parse module metadata <resource>'
        e2.cause.message == "The 'formatVersion' value should be the first value in a module metadata. Found 'other' instead."
    }

    def "fails when format version does not have string value"() {
        when:
        parser.parse(resource('{ "formatVersion": 1.2 }'))

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == "The 'formatVersion' value should have a string value."
    }

    def "fails on unsupported format version"() {
        when:
        parser.parse(resource('{ "formatVersion": "123.4" }'))

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse module metadata <resource>"
        e.cause.message == "Unsupported format version '123.4' specified in module metadata. This version of Gradle supports only format version 0.1."
    }

    def resource(String content) {
        def resource = Stub(LocallyAvailableExternalResource)
        _ * resource.displayName >> "<resource>"
        _ * resource.withContent(_) >> { Transformer transformer -> return transformer.transform(new ByteArrayInputStream(content.getBytes("utf-8"))) }
        resource
    }
}
