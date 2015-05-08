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

package org.gradle.model.internal.core;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Set;

public abstract class AbstractModelMap<T> implements ModelMap<T> {
    protected final ModelType<T> elementType;
    protected final ModelRuleDescriptor sourceDescriptor;
    protected final MutableModelNode modelNode;

    public AbstractModelMap(ModelType<T> elementType, MutableModelNode modelNode, ModelRuleDescriptor sourceDescriptor) {
        this.elementType = elementType;
        this.modelNode = modelNode;
        this.sourceDescriptor = sourceDescriptor;
    }

    @Override
    public int size() {
        return modelNode.getLinkCount(elementType);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Nullable
    @Override
    public T get(Object name) {
        return get((String) name);
    }

    @Nullable
    @Override
    public T get(String name) {
        // TODO - lock this down
        MutableModelNode link = modelNode.getLink(name);
        if (link == null) {
            return null;
        }
        link.ensureUsable();
        return link.asWritable(elementType, sourceDescriptor, null).getInstance();
    }

    @Override
    public boolean containsKey(Object name) {
        return name instanceof String && modelNode.hasLink((String) name, elementType);
    }

    @Override
    public Set<String> keySet() {
        return modelNode.getLinkNames(elementType);
    }

    @Override
    public void named(final String name, Action<? super T> configAction) {
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "named(%s)", name);
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.applyToLink(ModelActionRole.Mutate, new ActionBackedModelAction<T>(subject, descriptor, configAction));
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        modelNode.applyToLink(name, ruleSource);
    }

    @Override
    public void all(final Action<? super T> configAction) {
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "all()");
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, new ActionBackedModelAction<T>(subject, descriptor, configAction));
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "withType()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, new ActionBackedModelAction<S>(subject, descriptor, configAction));
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        modelNode.applyToLinks(type, rules);
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        doBeforeEach(elementType, configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        doBeforeEach(ModelType.of(type), configAction);
    }

    private <S> void doBeforeEach(ModelType<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "beforeEach()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Defaults, new ActionBackedModelAction<S>(subject, descriptor, configAction));
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        doFinalizeAll(elementType, configAction);
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        doFinalizeAll(ModelType.of(type), configAction);
    }

    @Override
    public Collection<T> values() {
        Iterable<T> values = Iterables.transform(keySet(), new Function<String, T>() {
            public T apply(@Nullable String name) {
                return get(name);
            }
        });
        return Lists.newArrayList(values);
    }

    private <S> void doFinalizeAll(ModelType<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "afterEach()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Finalize, new ActionBackedModelAction<S>(subject, descriptor, configAction));
    }
}
