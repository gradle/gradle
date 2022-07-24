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

import javax.annotation.Nullable;

/**
 * Criteria for selecting the descendants of a particular node.
 */
public abstract class NodePredicate {
    private final Predicate<? super MutableModelNode> matcher;

    private NodePredicate(Predicate<? super MutableModelNode> matcher) {
        this.matcher = matcher;
    }

    public ModelSpec scope(ModelPath scope) {
        return scope(scope, matcher);
    }

    protected abstract ModelSpec scope(ModelPath scope, Predicate<? super MutableModelNode> matcher);

    public static NodePredicate allLinks() {
        return allLinks(Predicates.<MutableModelNode>alwaysTrue());
    }

    public static NodePredicate allLinks(Predicate<? super MutableModelNode> predicate) {
        return new NodePredicate(predicate) {
            @Override
            protected ModelSpec scope(ModelPath scope, Predicate<? super MutableModelNode> matcher) {
                return new BasicPredicate(null, scope, null, matcher);
            }
        };
    }

    public static NodePredicate allDescendants() {
        return allDescendants(Predicates.<MutableModelNode>alwaysTrue());
    }

    public static NodePredicate allDescendants(Predicate<? super MutableModelNode> predicate) {
        return new NodePredicate(predicate) {
            @Override
            protected ModelSpec scope(ModelPath scope, Predicate<? super MutableModelNode> matcher) {
                return new BasicPredicate(null, null, scope, matcher);
            }
        };
    }

    public NodePredicate withType(Class<?> type) {
        return withType(ModelType.of(type));
    }

    public NodePredicate withType(ModelType<?> type) {
        final Predicate<MutableModelNode> matcher = ModelNodes.withType(type, this.matcher);
        final NodePredicate parent = this;
        return new NodePredicate(matcher) {
            @Override
            protected ModelSpec scope(ModelPath scope, Predicate<? super MutableModelNode> matcher) {
                return parent.scope(scope, matcher);
            }
        };
    }

    private static class BasicPredicate extends ModelSpec {
        private final ModelPath path;
        private final ModelPath parent;
        private final ModelPath ancestor;
        private final Predicate<? super MutableModelNode> matcher;

        public BasicPredicate(ModelPath path, ModelPath parent, ModelPath ancestor, Predicate<? super MutableModelNode> matcher) {
            this.path = path;
            this.parent = parent;
            this.ancestor = ancestor;
            this.matcher = matcher;
        }

        @Nullable
        @Override
        public ModelPath getPath() {
            return path;
        }

        @Nullable
        @Override
        public ModelPath getParent() {
            return parent;
        }

        @Nullable
        @Override
        public ModelPath getAncestor() {
            return ancestor;
        }

        @Override
        public boolean matches(MutableModelNode node) {
            return matcher.apply(node);
        }
    }
}
