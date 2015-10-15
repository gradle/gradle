/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.report.unbound;

import com.google.common.collect.ImmutableList;
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.Collection;

@ThreadSafe
public class UnboundRuleInput {

    private final String path;
    private final String type;
    private final boolean bound;
    private final String description;
    private final ImmutableList<String> suggestedPaths;
    private final String scope;

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public boolean isBound() {
        return bound;
    }

    public ImmutableList<? extends String> getSuggestedPaths() {
        return suggestedPaths;
    }

    public String getDescription() {
        return description;
    }

    public String getScope() {
        return scope;
    }

    private UnboundRuleInput(String path, String type, boolean bound, ImmutableList<String> suggestedPaths, String description, String scope) {
        this.path = path;
        this.type = type;
        this.bound = bound;
        this.suggestedPaths = suggestedPaths;
        this.description = description;
        this.scope = scope;
    }

    public static Builder type(String type) {
        return new Builder(type);
    }

    public static Builder type(Class<?> type) {
        return type(ModelType.of(type));
    }

    public static Builder type(ModelType<?> type) {
        return type(type.getDisplayName());
    }

    @NotThreadSafe
    public static class Builder {

        private String path;
        private String type;
        private boolean bound;
        private ImmutableList.Builder<String> suggestedPaths = ImmutableList.builder();
        private String description;
        private String scope;

        private Builder(String type) {
            this.type = type;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder path(ModelPath path) {
            return path(path.toString());
        }

        public Builder bound() {
            this.bound = true;
            return this;
        }

        public Builder suggestions(Collection<? extends String> suggestedPaths) {
            this.suggestedPaths.addAll(suggestedPaths);
            return this;
        }

        public Builder suggestions(String... suggestedPath) {
            return suggestions(Arrays.asList(suggestedPath));
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public UnboundRuleInput build() {
            return new UnboundRuleInput(path, type, bound, suggestedPaths.build(), description, scope);
        }
    }
}
