/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.internal.service;


import spock.lang.Specification

public class SynchronizedServiceRegistryTest extends Specification {

    def delegate = Mock(ServiceRegistry)
    def reg = new SynchronizedServiceRegistry(delegate);

    def "gets services from delegate"() {
        when:
        reg.get(Object)
        then:
        1 * delegate.get(Object)
        when:
        reg.getFactory(String)
        then:
        1 * delegate.getFactory(String)
        when:
        reg.newInstance(Integer)
        then:
        1 * delegate.newInstance(Integer)
    }
}
