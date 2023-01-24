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
package org.gradle.api.internal.artifacts.repositories.metadata;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

import java.util.List;
import java.util.Map;

public class DefaultMavenImmutableAttributesFactory implements MavenImmutableAttributesFactory {
    private final ImmutableAttributesFactory delegate;
    private final NamedObjectInstantiator objectInstantiator;
    private final Map<List<Object>, ImmutableAttributes> concatCache = Maps.newConcurrentMap();

    public DefaultMavenImmutableAttributesFactory(ImmutableAttributesFactory delegate, NamedObjectInstantiator objectInstantiator) {
        this.delegate = delegate;
        this.objectInstantiator = objectInstantiator;
    }

    @Override
    public AttributeContainerInternal mutable() {
        return delegate.mutable();
    }

    @Override
    public AttributeContainerInternal mutable(AttributeContainerInternal fallback) {
        return delegate.mutable(fallback);
    }

    @Override
    public AttributeContainerInternal join(AttributeContainerInternal fallback, AttributeContainerInternal primary) {
        return delegate.join(fallback, primary);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        return delegate.of(key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value) {
        return delegate.concat(node, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        return delegate.concat(node, key, value);
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes fallback, ImmutableAttributes primary) {
        return delegate.concat(fallback, primary);
    }

    @Override
    public ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException {
        return delegate.safeConcat(attributes1, attributes2);
    }

    @Override
    public ImmutableAttributes compileScope(ImmutableAttributes original) {
        List<Object> key = ImmutableList.of(original, Usage.JAVA_API);
        return concatCache.computeIfAbsent(key, k -> {
            ImmutableAttributes result = original;
            result = concat(result, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(Usage.JAVA_API, objectInstantiator));
            result = concat(result, FORMAT_ATTRIBUTE, new CoercingStringValueSnapshot(LibraryElements.JAR, objectInstantiator));
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(Category.LIBRARY, objectInstantiator));
            return result;
        });
    }

    @Override
    public ImmutableAttributes runtimeScope(ImmutableAttributes original) {
        List<Object> key = ImmutableList.of(original, Usage.JAVA_RUNTIME);
        return concatCache.computeIfAbsent(key, k -> {
            ImmutableAttributes result = original;
            result = concat(result, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(Usage.JAVA_RUNTIME, objectInstantiator));
            result = concat(result, FORMAT_ATTRIBUTE, new CoercingStringValueSnapshot(LibraryElements.JAR, objectInstantiator));
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(Category.LIBRARY, objectInstantiator));
            return result;
        });
    }

    @Override
    public ImmutableAttributes platformWithUsage(ImmutableAttributes original, String usage, boolean enforced) {
        String componentType = enforced ? Category.ENFORCED_PLATFORM : Category.REGULAR_PLATFORM;
        List<Object> key = ImmutableList.of(original, componentType, usage);
        return concatCache.computeIfAbsent(key, k -> {
            ImmutableAttributes result = original;
            result = concat(result, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(usage, objectInstantiator));
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(componentType, objectInstantiator));
            return result;
        });
    }

    @Override
    public ImmutableAttributes sourcesVariant(ImmutableAttributes original) {
        List<Object> key = ImmutableList.of(original, Category.DOCUMENTATION, Usage.JAVA_RUNTIME, DocsType.SOURCES);
        return concatCache.computeIfAbsent(key, k -> {
            ImmutableAttributes result = original;
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(Category.DOCUMENTATION, objectInstantiator));
            result = concat(result, Bundling.BUNDLING_ATTRIBUTE, objectInstantiator.named(Bundling.class, Bundling.EXTERNAL));
            result = concat(result, DocsType.DOCS_TYPE_ATTRIBUTE, objectInstantiator.named(DocsType.class, DocsType.SOURCES));
            result = concat(result, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(Usage.JAVA_RUNTIME, objectInstantiator));
            return result;
        });
    }

    @Override
    public ImmutableAttributes javadocVariant(ImmutableAttributes original) {
        List<Object> key = ImmutableList.of(original, Category.DOCUMENTATION, Usage.JAVA_RUNTIME, DocsType.JAVADOC);
        return concatCache.computeIfAbsent(key, k -> {
            ImmutableAttributes result = original;
            result = concat(result, CATEGORY_ATTRIBUTE, new CoercingStringValueSnapshot(Category.DOCUMENTATION, objectInstantiator));
            result = concat(result, Bundling.BUNDLING_ATTRIBUTE, objectInstantiator.named(Bundling.class, Bundling.EXTERNAL));
            result = concat(result, DocsType.DOCS_TYPE_ATTRIBUTE, objectInstantiator.named(DocsType.class, DocsType.JAVADOC));
            result = concat(result, USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(Usage.JAVA_RUNTIME, objectInstantiator));
            return result;
        });
    }
}
