/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api

import groovy.transform.SelfType
import spock.lang.Issue

@SelfType(AbstractDomainObjectContainerIntegrationTest)
trait AbstractNamedDomainObjectContainerIntegrationTest {
    String getContainerStringRepresentation() {
        return "SomeType container"
    }

    String makeContainer() {
        return "project.container(SomeType)"
    }

    static String getContainerType() {
        return "NamedDomainObjectContainer"
    }

    def setup() {
        settingsFile << """
            class SomeType implements Named {
                final String name

                SomeType(String name) {
                    this.name = name
                }
            }
        """
    }
}


class NamedDomainObjectContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest implements AbstractNamedDomainObjectContainerIntegrationTest {
    def "can mutate the task container from named container"() {
        buildFile """
            testContainer.configureEach {
                tasks.create(it.name)
            }
            toBeRealized.get()

            task verify {
                doLast {
                    assert tasks.findByName("realized") != null
                    assert tasks.findByName("toBeRealized") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }

    def "chained lookup of testContainer.withType.matching"() {
        buildFile << """
            testContainer.withType(testContainer.type).matching({ it.name.endsWith("foo") }).all { element ->
                assert element.name in ['foo', 'barfoo']
            }

            testContainer.register("foo")
            testContainer.register("bar")
            testContainer.register("foobar")
            testContainer.register("barfoo")
        """
        expect:
        succeeds "help"
    }

    @Issue("https://github.com/gradle/gradle/issues/9446")
    def "chained lookup of testContainer.matching.withType"() {
        buildFile << """
            testContainer.matching({ it.name.endsWith("foo") }).withType(testContainer.type).all { element ->
                assert element.name in ['foo', 'barfoo']
            }

            testContainer.register("foo")
            testContainer.register("bar")
            testContainer.register("foobar")
            testContainer.register("barfoo")
        """
        expect:
        succeeds "help"
    }

    def "name based filtering"() {
        buildFile << """
            def onlyFoo = testContainer.named({ it.endsWith("foo") })

            assert onlyFoo.isEmpty()
            
            testContainer.register("foo")
            testContainer.register("bar")
            testContainer.register("foobar")
            testContainer.register("barfoo")
            
            assert onlyFoo*.name == ["foo", "barfoo"]
        """
        expect:
        succeeds "help"
    }

    def "custom collection not implementing named method triggers useful error message"() {
        buildFile << """
            objects.newInstance(MyNamedDomainObjectCollection).named { !it.isEmpty() }

            import javax.annotation.Nullable;

            public class MyNamedDomainObjectCollection<T> implements NamedDomainObjectCollection<T> {
                @Override
                public void addLater(Provider<? extends T> provider) {

                }

                @Override
                public void addAllLater(Provider<? extends Iterable<T>> provider) {

                }

                @Override
                public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
                    return null;
                }

                @Override
                public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
                    return null;
                }

                @Override
                public Action<? super T> whenObjectAdded(Action<? super T> action) {
                    return null;
                }

                @Override
                public void whenObjectAdded(Closure action) {

                }

                @Override
                public Action<? super T> whenObjectRemoved(Action<? super T> action) {
                    return null;
                }

                @Override
                public void whenObjectRemoved(Closure action) {

                }

                @Override
                public void all(Action<? super T> action) {

                }

                @Override
                public void all(Closure action) {

                }

                @Override
                public void configureEach(Action<? super T> action) {

                }

                @Override
                public Collection<T> findAll(Closure spec) {
                    return null;
                }

                @Override
                public boolean add(T e) {
                    return false;
                }

                @Override
                public boolean addAll(Collection<? extends T> c) {
                    return false;
                }

                @Override
                public Namer<T> getNamer() {
                    return null;
                }

                @Override
                public SortedMap<String, T> getAsMap() {
                    return null;
                }

                @Override
                public SortedSet<String> getNames() {
                    return null;
                }

                @Nullable
                @Override
                public T findByName(String name) {
                    return null;
                }

                @Override
                public T getByName(String name) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public T getByName(String name, Action<? super T> configureAction) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public T getAt(String name) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public Rule addRule(Rule rule) {
                    return null;
                }

                @Override
                public Rule addRule(String description, Closure ruleAction) {
                    return null;
                }

                @Override
                public Rule addRule(String description, Action<String> ruleAction) {
                    return null;
                }

                @Override
                public List<Rule> getRules() {
                    return null;
                }

                @Override
                public <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type) {
                    return null;
                }

                @Override
                public NamedDomainObjectCollection<T> matching(Spec<? super T> spec) {
                    return null;
                }

                @Override
                public NamedDomainObjectCollection<T> matching(Closure spec) {
                    return null;
                }

                @Override
                public NamedDomainObjectProvider<T> named(String name) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public NamedDomainObjectProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException {
                    return null;
                }

                @Override
                public NamedDomainObjectCollectionSchema getCollectionSchema() {
                    return null;
                }

                @Override
                public int size() {
                    return 0;
                }

                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public boolean contains(Object o) {
                    return false;
                }

                @Override
                public Iterator<T> iterator() {
                    return null;
                }

                @Override
                public Object[] toArray() {
                    return new Object[0];
                }

                @Override
                public <T1> T1[] toArray(T1[] a) {
                    return null;
                }

                @Override
                public boolean remove(Object o) {
                    return false;
                }

                @Override
                public boolean containsAll(Collection<?> c) {
                    return false;
                }

                @Override
                public boolean removeAll(Collection<?> c) {
                    return false;
                }

                @Override
                public boolean retainAll(Collection<?> c) {
                    return false;
                }

                @Override
                public void clear() {

                }
            }
        """
        expect:
        fails("help").assertHasCause("Method not implemented in MyNamedDomainObjectCollection")
    }
}
