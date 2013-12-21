/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Namer
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultNamedDomainObjectCollectionTest extends Specification {

    private final Namer<Bean> namer = new Namer<Bean>() {
        public String determineName(Bean bean) { return bean.name; }
    };

    Instantiator instantiator = Mock(Instantiator)

    private final DefaultNamedDomainObjectCollection<Bean> container = new DefaultNamedDomainObjectCollection<CharSequence>(Bean, new HashSet<>(), instantiator, namer);
    Set<Bean> store

    def setup() {
        container.clear()
    }

    def "getNames"() {
        expect:
        container.getNames().isEmpty()

        when:
        container.add(new Bean("bean1"))
        container.add(new Bean("bean2"))
        container.add(new Bean("bean3"))
        then:
        container.names == ["bean1", "bean2", "bean3"] as SortedSet
    }

    private class Bean {
        public final String name;

        public Bean(String name) {
            this.name = name;
        }
    }
}
