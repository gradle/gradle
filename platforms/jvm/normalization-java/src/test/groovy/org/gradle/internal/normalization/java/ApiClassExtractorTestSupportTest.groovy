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

package org.gradle.internal.normalization.java


class ApiClassExtractorTestSupportTest extends ApiClassExtractorTestSupport {

    def "should create implementation class for #fqn"() {
        given:
        def clazz = toClass(fqn, src).clazz

        expect:
        clazz.name == fqn

        where:
        fqn          | src
        'A'          | 'public class A {}'
        'com.acme.A' | '''
                           package com.acme;
                           public class A {}
                       '''
        'com.acme.B' | '''
                           package com.acme;
                           public class B {
                               String getName() { return "foo"; }
                           }
                       '''
    }

    def "should compile classes together"() {
        given:
        def api = toApi([
            'com.acme.A': '''
                package com.acme;
                public class A extends B {}
            ''',
            'com.acme.B': '''
                package com.acme;
                public class B {
                    public String getId() { return "id"; }
                }
            '''
        ])

        when:
        def a = api.classes['com.acme.A'].clazz
        def b = api.classes['com.acme.B'].clazz

        then:
        a.name == 'com.acme.A'
        b.name == 'com.acme.B'

        when:
        def aa = createInstance(a)

        then:
        aa.id == 'id'
    }
}
