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

package org.gradle.language.base.internal.tasks.apigen

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires(TestPrecondition.JDK6_OR_LATER)
class ApiStubGeneratorValidationTest extends ApiStubGeneratorTestSupport {
    @Unroll
    def "should not throw an error if exposing a JDK class #type in method return type"() {
        given:
        def api = toApi (['com.acme'], ['com.acme.A': """package com.acme;

public abstract class A {
    public abstract $type foo();
    public abstract $type[] bar();
}"""])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        noExceptionThrown()

        where:
        type << ['String', 'boolean', 'byte', 'short', 'char', 'int', 'long', 'float', 'double']
    }
}
