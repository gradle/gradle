/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.annotation.Nullable

class CustomNamedDomainObjectCollectionTest extends Specification {

    def "custom collection not implementing named method triggers useful error message"() {
        when:
        TestUtil.objectFactory().newInstance(MyNamedDomainObjectCollection).named { !it.isEmpty() }

        then:
        UnsupportedOperationException e = thrown()
        e.getMessage() == "Method not implemented by org.gradle.api.internal.MyNamedDomainObjectCollection"
    }

}

class MyNamedDomainObjectCollection<T> implements NamedDomainObjectCollection<T> {
    @Override
    void addLater(Provider<? extends T> provider) {

    }

    @Override
    void addAllLater(Provider<? extends Iterable<T>> provider) {

    }

    @Override
    <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return null
    }

    @Override
    <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return null
    }

    @Override
    Action<? super T> whenObjectAdded(Action<? super T> action) {
        return null
    }

    @Override
    void whenObjectAdded(Closure action) {

    }

    @Override
    Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return null
    }

    @Override
    void whenObjectRemoved(Closure action) {

    }

    @Override
    void all(Action<? super T> action) {

    }

    @Override
    void all(Closure action) {

    }

    @Override
    void configureEach(Action<? super T> action) {

    }

    @Override
    Collection<T> findAll(Closure spec) {
        return null
    }

    @Override
    boolean add(T e) {
        return false
    }

    @Override
    boolean addAll(Collection<? extends T> c) {
        return false
    }

    @Override
    Namer<T> getNamer() {
        return null
    }

    @Override
    SortedMap<String, T> getAsMap() {
        return null
    }

    @Override
    SortedSet<String> getNames() {
        return null
    }

    @Nullable
    @Override
    T findByName(String name) {
        return null
    }

    @Override
    T getByName(String name) throws UnknownDomainObjectException {
        return null
    }

    @Override
    T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        return null
    }

    @Override
    T getByName(String name, Action<? super T> configureAction) throws UnknownDomainObjectException {
        return null
    }

    @Override
    T getAt(String name) throws UnknownDomainObjectException {
        return null
    }

    @Override
    Rule addRule(Rule rule) {
        return null
    }

    @Override
    Rule addRule(String description, Closure ruleAction) {
        return null
    }

    @Override
    Rule addRule(String description, Action<String> ruleAction) {
        return null
    }

    @Override
    List<Rule> getRules() {
        return null
    }

    @Override
    <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type) {
        return null
    }

    @Override
    NamedDomainObjectCollection<T> matching(Spec<? super T> spec) {
        return null
    }

    @Override
    NamedDomainObjectCollection<T> matching(Closure spec) {
        return null
    }

    @Override
    NamedDomainObjectProvider<T> named(String name) throws UnknownDomainObjectException {
        return null
    }

    @Override
    NamedDomainObjectProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownDomainObjectException {
        return null
    }

    @Override
    <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException {
        return null
    }

    @Override
    <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException {
        return null
    }

    @Override
    NamedDomainObjectCollectionSchema getCollectionSchema() {
        return null
    }

    @Override
    int size() {
        return 0
    }

    @Override
    boolean isEmpty() {
        return false
    }

    @Override
    boolean contains(Object o) {
        return false
    }

    @Override
    Iterator<T> iterator() {
        return null
    }

    @Override
    Object[] toArray() {
        return new Object[0]
    }

    @Override
    <T1> T1[] toArray(T1[] a) {
        return null
    }

    @Override
    boolean remove(Object o) {
        return false
    }

    @Override
    boolean containsAll(Collection<?> c) {
        return false
    }

    @Override
    boolean removeAll(Collection<?> c) {
        return false
    }

    @Override
    boolean retainAll(Collection<?> c) {
        return false
    }

    @Override
    void clear() {

    }
}
