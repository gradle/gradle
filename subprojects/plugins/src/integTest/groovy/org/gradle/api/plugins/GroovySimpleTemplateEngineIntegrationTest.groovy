/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins

import groovy.text.SimpleTemplateEngine
import org.gradle.api.UncheckedIOException
import org.gradle.api.internal.plugins.GroovySimpleTemplateEngine
import org.gradle.api.internal.plugins.TemplateEngine
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.UncheckedException

class GroovySimpleTemplateEngineIntegrationTest extends AbstractIntegrationSpec {
    TemplateEngine templateEngine = new GroovySimpleTemplateEngine()

    def "can generate content from template file"() {
        given:
        File template = file('mytemplate.txt') << 'Hello from "$language"!'
        def binding = ['language': 'Groovy']

        when:
        String content = templateEngine.generate(template, binding)

        then:
        content == 'Hello from "Groovy"!'
    }

    def "rethrows ClassNotFoundException wrapped as UncheckedException"() {
        given:
        SimpleTemplateEngine simpleTemplateEngine = Spy(SimpleTemplateEngine)
        templateEngine.engine = simpleTemplateEngine
        File template = file('mytemplate.txt') << ''
        def binding = [:]

        when:
        templateEngine.generate(template, binding)

        then:
        simpleTemplateEngine.createTemplate(_) >> { String templateText -> throw new ClassNotFoundException() }
        thrown(UncheckedException)
    }

    def "rethrows IOException wrapped as UncheckedIOException"() {
        given:
        SimpleTemplateEngine simpleTemplateEngine = Spy(SimpleTemplateEngine)
        templateEngine.engine = simpleTemplateEngine
        File template = file('mytemplate.txt') << ''
        def binding = [:]

        when:
        templateEngine.generate(template, binding)

        then:
        simpleTemplateEngine.createTemplate(_) >> { String templateText -> throw new IOException() }
        thrown(UncheckedIOException)
    }
}
