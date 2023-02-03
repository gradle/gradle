/*
 * Copyright 2008 the original author or authors.
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

import org.gradle.api.Transformer
import spock.lang.Specification

class ChainingTransformerTest extends Specification {
    private final ChainingTransformer<String> transformer = new ChainingTransformer<String>(String.class)

    def 'does nothing when no transformers added'() {
        expect:
        transformer.transform('value') == 'value'
    }

    def 'passes object to each Transformer in turn'() {
        given:
        Transformer<String, String> transformerA = Mock()
        Transformer<String, String> transformerB = Mock()

        transformerA.transform('original') >> 'a'
        transformerB.transform('a') >> 'b'

        transformer.add(transformerA)
        transformer.add(transformerB)

        expect:
        transformer.transform('original') == 'b'

    }

    def 'can use a Closure as a Transformer'() {
        when:
        transformer.add { it + ' transformed'}

        then:
        transformer.transform('original') == 'original transformed'
    }

    def 'uses original object when Closure returns null'() {
        when:
        transformer.add { null }

        then:
        transformer.transform('original') == 'original'
    }

    def 'uses original object when Closure returns object of unexpected type'() {
        when:
        transformer.add { 9 }

        then:
        transformer.transform('original') == 'original'
    }

    def 'original object is set as delegate for Closure'() {
        when:
        transformer.add { substring(1, 3) }

        then:
        transformer.transform('original') == 'ri'
    }

    def 'closure can transform a String into a GString'() {
        when:
        transformer.add { "[$it]" }

        then:
        transformer.transform('original') == '[original]'
    }
}
