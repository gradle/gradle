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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.gradle.model.internal.type.ModelType;

public class ModelNodes {
    public static Predicate<MutableModelNode> all() {
        return Predicates.alwaysTrue();
    }

    public static Predicate<MutableModelNode> withType(Class<?> type) {
        return withType(ModelType.of(type));
    }

    public static Predicate<MutableModelNode> withType(final ModelType<?> type) {
        return withType(type, Predicates.<MutableModelNode>alwaysTrue());
    }

    public static Predicate<MutableModelNode> withType(Class<?> type, Predicate<? super MutableModelNode> predicate) {
        return withType(ModelType.of(type), predicate);
    }

    public static Predicate<MutableModelNode> withType(final ModelType<?> type, final Predicate<? super MutableModelNode> predicate) {
        return new Predicate<MutableModelNode>() {
            @Override
            public boolean apply(MutableModelNode node) {
                node.ensureAtLeast(ModelNode.State.Discovered);
                return node.canBeViewedAs(type) && predicate.apply(node);
            }
        };
    }
}
