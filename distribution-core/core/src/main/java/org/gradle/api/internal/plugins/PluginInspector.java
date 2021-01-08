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

package org.gradle.api.internal.plugins;

import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Plugin;
import org.gradle.internal.Cast;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

@ThreadSafe
public class PluginInspector {

    private final ModelRuleSourceDetector modelRuleSourceDetector;

    public PluginInspector(ModelRuleSourceDetector modelRuleSourceDetector) {
        this.modelRuleSourceDetector = modelRuleSourceDetector;
    }

    public <T> PotentialPlugin<T> inspect(Class<T> type) {
        boolean implementsInterface = Plugin.class.isAssignableFrom(type);
        boolean hasRules = this.modelRuleSourceDetector.hasRules(type);

        if (implementsInterface) {
            @SuppressWarnings("unchecked") Class<? extends Plugin<?>> cast = (Class<? extends Plugin<?>>) type;
            return Cast.uncheckedCast(toImperative(cast, hasRules));
        } else if (hasRules) {
            return new PotentialPureRuleSourceClassPlugin<T>(type);
        } else {
            return new PotentialUnknownTypePlugin<T>(type);
        }
    }

    private <T extends Plugin<?>> PotentialPlugin<T> toImperative(Class<T> type, boolean hasRules) {
        if (hasRules) {
            return new PotentialHybridImperativeAndRulesPlugin<T>(type);
        } else {
            return new PotentialImperativeClassPlugin<T>(type);
        }
    }

    private static class PotentialImperativeClassPlugin<T extends Plugin<?>> implements PotentialPlugin<T> {

        private final Class<T> clazz;

        public PotentialImperativeClassPlugin(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Class<T> asClass() {
            return clazz;
        }

        @Override
        public boolean isImperative() {
            return true;
        }

        @Override
        public Type getType() {
            return Type.IMPERATIVE_CLASS;
        }

        @Override
        public boolean isHasRules() {
            return false;
        }
    }

    private static class PotentialHybridImperativeAndRulesPlugin<T extends Plugin<?>> implements PotentialPlugin<T> {

        private final Class<T> clazz;

        public PotentialHybridImperativeAndRulesPlugin(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Class<T> asClass() {
            return clazz;
        }

        @Override
        public boolean isImperative() {
            return true;
        }

        @Override
        public boolean isHasRules() {
            return true;
        }

        @Override
        public Type getType() {
            return Type.HYBRID_IMPERATIVE_AND_RULES_CLASS;
        }

    }

    private static class PotentialPureRuleSourceClassPlugin<T> implements PotentialPlugin<T> {

        private final Class<T> clazz;

        public PotentialPureRuleSourceClassPlugin(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Class<T> asClass() {
            return clazz;
        }

        @Override
        public boolean isImperative() {
            return false;
        }

        @Override
        public Type getType() {
            return Type.PURE_RULE_SOURCE_CLASS;
        }

        @Override
        public boolean isHasRules() {
            return false;
        }
    }

    private static class PotentialUnknownTypePlugin<T> implements PotentialPlugin<T> {

        private final Class<T> clazz;

        public PotentialUnknownTypePlugin(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Class<T> asClass() {
            return clazz;
        }

        @Override
        public boolean isImperative() {
            return false;
        }

        @Override
        public boolean isHasRules() {
            return false;
        }

        @Override
        public Type getType() {
            return Type.UNKNOWN;
        }

    }
}
