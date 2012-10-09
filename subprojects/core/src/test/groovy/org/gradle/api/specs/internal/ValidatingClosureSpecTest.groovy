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

package org.gradle.api.specs.internal

import org.gradle.api.GradleException
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 10/9/12
 */
class ValidatingClosureSpecTest extends Specification {

    def "converts closure to spec"() {
        expect:
        new ValidatingClosureSpec({ new Boolean(true) }, "someTask").isSatisfiedBy('foo')
        !new ValidatingClosureSpec({ new Boolean(false) }, "someTask").isSatisfiedBy('foo')
        new ValidatingClosureSpec({ true }, "someTask").isSatisfiedBy('foo')
        !new ValidatingClosureSpec({ false }, "someTask").isSatisfiedBy('foo')

        def spec = new ValidatingClosureSpec({ it.startsWith('foo') }, "someTask")
        spec.isSatisfiedBy("foo bar")
        !spec.isSatisfiedBy("bar foo")
    }

    def "reports incorrect closure"() {
        when:
        new ValidatingClosureSpec({ println it }, "someTask.something").isSatisfiedBy("foo")
        then:
        def ex = thrown(GradleException)
        ex.message == 'Closure: someTask.something must return boolean but it but it returned: null'
    }
}
