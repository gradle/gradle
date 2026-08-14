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

import org.gradle.api.Plugin;
import org.gradle.internal.Cast;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;

@ServiceScope(Scope.Global.class)
@ThreadSafe
public class PluginInspector {

    public <T> PotentialPlugin<T> inspect(Class<T> type) {
        if (Plugin.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked") Class<? extends Plugin<?>> cast = (Class<? extends Plugin<?>>) type;
            return Cast.uncheckedCast(new PotentialImperativeClassPlugin<>(cast));
        }
        return new PotentialUnknownTypePlugin<>(type);
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
        public Type getType() {
            return Type.UNKNOWN;
        }
    }
}
