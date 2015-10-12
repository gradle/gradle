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
        def api = toApi(['com.acme'], ['com.acme.A': """package com.acme;

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

    @Unroll
    def "should throw an error if an implementation class is exposed in the public API in a #descriptor"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import java.util.List;

public class A {
    $method
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

'''])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == "In $methodDescriptor, type com.acme.internal.AImpl is exposed in the public API but doesn't belong to the allowed packages."

        where:
        descriptor                  | method                                           | methodDescriptor
        'return type'               | 'public AImpl getImpl() { return new AImpl(); }' | 'public com.acme.internal.AImpl getImpl()'
        'parameter'                 | 'public void getImpl(AImpl impl) { }'            | 'public void getImpl(com.acme.internal.AImpl)'
        'generic type'              | 'public List<AImpl> getImpl() { return null; }'  | 'public java.util.List getImpl()'
        'generic type in parameter' | 'public void getImpl(List<AImpl> impls) { }'     | 'public void getImpl(java.util.List)'
    }

    def "should throw an error listing all invalid exposed types for a single method"() {
        given:
        def api = toApi(['com.acme'], ['com.acme.A'             : """package com.acme;
import com.acme.internal.AImpl;
import com.acme.internal.AInternal;

public class A {
    public AImpl toImpl(AInternal internal) { return null; }
}""",
                                       'com.acme.internal.AImpl': '''package com.acme.internal;
public class AImpl {}

''',
                                       'com.acme.internal.AInternal': '''package com.acme.internal;
public class AInternal {}

'''])

        when:
        api.loadStub(api.classes['com.acme.A'])

        then:
        def ex = thrown(InvalidPublicAPIException)
        ex.message == """The following types are referenced in public com.acme.internal.AImpl toImpl(com.acme.internal.AInternal) but don't belong to the allowed packages:
   - com.acme.internal.AInternal
   - com.acme.internal.AImpl
"""
    }
}
