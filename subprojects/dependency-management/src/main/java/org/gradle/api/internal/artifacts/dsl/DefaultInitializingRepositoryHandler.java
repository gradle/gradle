/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.ExclusiveContentRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.DefaultNamedDomainObjectCollection;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.metaobject.PropertyMixIn;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

@SuppressWarnings("rawtypes")
public class DefaultInitializingRepositoryHandler implements ArtifactRepositoryContainer, RepositoryHandler, MethodMixIn, PropertyMixIn {
    private final Lazy<DefaultRepositoryHandler> reader;
    private final Lazy<DefaultRepositoryHandler> writer;

    public DefaultInitializingRepositoryHandler(DefaultRepositoryHandler delegate,
                                                 Action<? super RepositoryHandler> onReadAction,
                                                 Action<? super RepositoryHandler> onWriteAction) {
        this.reader = Lazy.unsafe().of(() -> {
            onReadAction.execute(delegate);
            return delegate;
        });
        this.writer = Lazy.unsafe().of(() -> {
            onWriteAction.execute(delegate);
            return delegate;
        });
    }

    private DefaultRepositoryHandler reader() {
        return reader.get();
    }

    private DefaultRepositoryHandler writer() {
        return writer.get();
    }

    public Class<? extends ArtifactRepository> getType() {
        return reader().getType();
    }

    @Override
    public Iterator<ArtifactRepository> iterator() {
        return reader().iterator();
    }

    @Override
    public Object[] toArray() {
        return reader().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return reader().toArray(a);
    }

    @Override
    public void all(Action<? super ArtifactRepository> action) {
        reader().all(action);
    }

    @Override
    public void configureEach(Action<? super ArtifactRepository> action) {
        reader().configureEach(action);
    }

    @Override
    public void all(Closure action) {
        reader().all(action);
    }

    @Override
    public <S extends ArtifactRepository> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return reader().withType(type, configureAction);
    }

    @Override
    public <S extends ArtifactRepository> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return reader().withType(type, configureClosure);
    }

    @Override
    public Action<? super ArtifactRepository> whenObjectAdded(Action<? super ArtifactRepository> action) {
        return reader().whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        reader().whenObjectAdded(action);
    }

    @Override
    public Action<? super ArtifactRepository> whenObjectRemoved(Action<? super ArtifactRepository> action) {
        return reader().whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        reader().whenObjectRemoved(action);
    }

    @Override
    public boolean add(ArtifactRepository toAdd) {
        return writer().add(toAdd);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<ArtifactRepository>> provider) {
        writer().addAllLater(provider);
    }

    @Override
    public boolean contains(Object o) {
        return reader().contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return reader().containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        return reader().isEmpty();
    }

    @Override
    public boolean remove(Object o) {
        return writer().remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return writer().removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> target) {
        return writer().retainAll(target);
    }

    @Override
    public int size() {
        return writer().size();
    }

    public int estimatedSize() {
        return reader().estimatedSize();
    }

    public MutationGuard getMutationGuard() {
        return reader().getMutationGuard();
    }

    @Override
    public boolean addAll(Collection<? extends ArtifactRepository> c) {
        return writer().addAll(c);
    }

    @Override
    public void addLater(Provider<? extends ArtifactRepository> provider) {
        writer().addLater(provider);
    }

    public void whenElementKnown(Action<? super DefaultNamedDomainObjectCollection.ElementInfo<ArtifactRepository>> action) {
        reader().whenElementKnown(action);
    }

    @Override
    public void clear() {
        writer().clear();
    }

    @Override
    public Namer<ArtifactRepository> getNamer() {
        return reader().getNamer();
    }

    public String getDisplayName() {
        return reader().getDisplayName();
    }

    @Override
    public String toString() {
        return reader().toString();
    }

    @Override
    public SortedMap<String, ArtifactRepository> getAsMap() {
        return reader().getAsMap();
    }

    @Override
    public SortedSet<String> getNames() {
        return reader().getNames();
    }

    @Nullable
    @Override
    public ArtifactRepository findByName(String name) {
        return reader().findByName(name);
    }

    @Override
    public ArtifactRepository getByName(String name) throws UnknownDomainObjectException {
        return reader().getByName(name);
    }

    @Override
    public ArtifactRepository getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        return reader().getByName(name, configureClosure);
    }

    @Override
    public ArtifactRepository getByName(String name, Action<? super ArtifactRepository> configureAction) throws UnknownDomainObjectException {
        return reader().getByName(name, configureAction);
    }

    @Override
    public ArtifactRepository getAt(String name) throws UnknownDomainObjectException {
        return reader().getAt(name);
    }

    @Override
    public NamedDomainObjectProvider<ArtifactRepository> named(String name) throws UnknownDomainObjectException {
        return reader().named(name);
    }

    @Override
    public NamedDomainObjectProvider<ArtifactRepository> named(String name, Action<? super ArtifactRepository> configurationAction) throws UnknownDomainObjectException {
        return reader().named(name, configurationAction);
    }

    @Override
    public <S extends ArtifactRepository> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException {
        return reader().named(name, type);
    }

    @Override
    public <S extends ArtifactRepository> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException {
        return reader().named(name, type, configurationAction);
    }

    public MethodAccess getAdditionalMethods() {
        return reader().getAdditionalMethods();
    }

    public PropertyAccess getAdditionalProperties() {
        return reader().getAdditionalProperties();
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return reader().getCollectionSchema();
    }

    @Override
    public Rule addRule(Rule rule) {
        return writer().addRule(rule);
    }

    @Override
    public Rule addRule(String description, Closure ruleAction) {
        return writer().addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(String description, Action<String> ruleAction) {
        return writer().addRule(description, ruleAction);
    }

    @Override
    public List<Rule> getRules() {
        return reader().getRules();
    }

    @Override
    public void add(int index, ArtifactRepository element) {
        writer().add(index, element);
    }

    @Override
    public boolean addAll(int index, Collection<? extends ArtifactRepository> c) {
        return writer().addAll(index, c);
    }

    @Override
    public ArtifactRepository get(int index) {
        return reader().get(index);
    }

    @Override
    public ArtifactRepository set(int index, ArtifactRepository element) {
        return writer().set(index, element);
    }

    @Override
    public ArtifactRepository remove(int index) {
        return writer().remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return reader().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return reader().lastIndexOf(o);
    }

    @Override
    public ListIterator<ArtifactRepository> listIterator() {
        return reader().listIterator();
    }

    @Override
    public ListIterator<ArtifactRepository> listIterator(int index) {
        return reader().listIterator(index);
    }

    @Override
    public List<ArtifactRepository> subList(int fromIndex, int toIndex) {
        return reader().subList(fromIndex, toIndex);
    }

    @Override
    public NamedDomainObjectList<ArtifactRepository> matching(Closure spec) {
        return reader().matching(spec);
    }

    @Override
    public NamedDomainObjectList<ArtifactRepository> matching(Spec<? super ArtifactRepository> spec) {
        return reader().matching(spec);
    }

    @Override
    public <S extends ArtifactRepository> NamedDomainObjectList<S> withType(Class<S> type) {
        return reader().withType(type);
    }

    @Override
    public List<ArtifactRepository> findAll(Closure cl) {
        return reader().findAll(cl);
    }

    public String getTypeDisplayName() {
        return reader().getTypeDisplayName();
    }

    @Override
    public DefaultArtifactRepositoryContainer configure(Closure closure) {
        return writer().configure(closure);
    }

    @Override
    public void addFirst(ArtifactRepository repository) {
        writer().addFirst(repository);
    }

    @Override
    public void addLast(ArtifactRepository repository) {
        writer().addLast(repository);
    }

    public <T extends ArtifactRepository> T addRepository(T repository, String defaultName) {
        return writer().addRepository(repository, defaultName);
    }

    public <T extends ArtifactRepository> T addRepository(T repository, String defaultName, Action<? super T> configureAction) {
        return writer().addRepository(repository, defaultName, configureAction);
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action) {
        return writer().flatDir(action);
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Closure configureClosure) {
        return writer().flatDir(configureClosure);
    }

    @Override
    public FlatDirectoryArtifactRepository flatDir(Map<String, ?> args) {
        return writer().flatDir(args);
    }

    @Override
    public ArtifactRepository gradlePluginPortal() {
        return writer().gradlePluginPortal();
    }

    @Override
    public ArtifactRepository gradlePluginPortal(Action<? super ArtifactRepository> action) {
        return writer().gradlePluginPortal(action);
    }

    @Override
    public MavenArtifactRepository mavenCentral() {
        return writer().mavenCentral();
    }

    @Override
    public MavenArtifactRepository mavenCentral(Action<? super MavenArtifactRepository> action) {
        return writer().mavenCentral(action);
    }

    @Override
    public MavenArtifactRepository jcenter() {
        return writer().jcenter();
    }

    @Override
    public MavenArtifactRepository jcenter(Action<? super MavenArtifactRepository> action) {
        return writer().jcenter(action);
    }

    @Override
    public MavenArtifactRepository mavenCentral(Map<String, ?> args) {
        return writer().mavenCentral(args);
    }

    @Override
    public MavenArtifactRepository mavenLocal() {
        return writer().mavenLocal();
    }

    @Override
    public MavenArtifactRepository mavenLocal(Action<? super MavenArtifactRepository> action) {
        return writer().mavenLocal(action);
    }

    @Override
    public MavenArtifactRepository google() {
        return writer().google();
    }

    @Override
    public MavenArtifactRepository google(Action<? super MavenArtifactRepository> action) {
        return writer().google(action);
    }

    @Override
    public MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action) {
        return writer().maven(action);
    }

    @Override
    public MavenArtifactRepository maven(Closure closure) {
        return writer().maven(closure);
    }

    @Override
    public IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action) {
        return writer().ivy(action);
    }

    @Override
    public IvyArtifactRepository ivy(Closure closure) {
        return writer().ivy(closure);
    }

    @Override
    public void exclusiveContent(Action<? super ExclusiveContentRepository> action) {
        writer().exclusiveContent(action);
    }

}
