/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives.internal

import org.gradle.api.java.archives.ManifestException
import spock.lang.Specification

class DefaultAttributesTest extends Specification {
    DefaultAttributes attributes = new DefaultAttributes();

    def testKeyValidationWithPut() {
        when:
        attributes.put(':::', 'someValue')

        then:
        thrown(ManifestException)

        when:
        attributes.put(null, 'someValue')

        then:
        thrown(NullPointerException)
    }

    def testKeyValidationWithPutAll() {
        when:
        attributes.putAll(':::': 'someValue')

        then:
        thrown(ManifestException)

        when:
        attributes.putAll((null): 'someValue')

        then:
        thrown(NullPointerException)
    }

    def testValueValidationWithPut() {
        when:
        attributes.put('key', null)

        then:
        thrown(NullPointerException)
    }

    def testValueValidationWithPutAll() {
        when:
        attributes.putAll('key': null)

        then:
        thrown(NullPointerException)
    }

    def caseSensitivity() {
        when:
        attributes.put("Hello", "world")
        attributes.put("hello", "keys are case-insensitive")

        then:
        attributes.get("Hello") == "keys are case-insensitive"
        attributes.get("hello") == "keys are case-insensitive"
    }
}
