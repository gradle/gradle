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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UnboundRuleInput {

    private final String path;
    private final String type;
    private final boolean bound;
    private final List<String> suggestedPaths;

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public boolean isBound() {
        return bound;
    }

    public Collection<String> getSuggestedPaths() {
        return suggestedPaths;
    }

    public UnboundRuleInput(String path, String type, boolean bound, List<String> suggestedPaths) {
        this.path = path;
        this.type = type;
        this.bound = bound;
        this.suggestedPaths = suggestedPaths;
    }

    public static Builder type(String type) {
        return new Builder(type);
    }

    public static class Builder {

        private String path;
        private String type;
        private boolean bound;
        private List<String> suggestedPaths = new ArrayList<String>();

        public Builder(String type) {
            this.type = type;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder bound() {
            this.bound = true;
            return this;
        }

        public Builder suggestions(Collection<String> suggestedPaths) {
            this.suggestedPaths.addAll(suggestedPaths);
            return this;
        }

        public Builder suggestion(String suggestedPath) {
            suggestedPaths.add(suggestedPath);
            return this;
        }

        public UnboundRuleInput build() {
            return new UnboundRuleInput(path, type, bound, suggestedPaths);
        }
    }
}
