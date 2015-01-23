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

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.PluginManager;
import org.gradle.plugin.internal.PluginId;

public interface PluginManagerInternal extends PluginManager {
    void apply(PluginImplementation<?> plugin);

    <P extends Plugin> P addImperativePlugin(PluginImplementation<P> plugin);

    <P extends Plugin> P addImperativePlugin(Class<P> plugin);

    PluginContainer getPluginContainer();

    DomainObjectSet<PluginWithId> pluginsForId(String id);

    class PluginWithId {
        final PluginId id;
        final Class<?> clazz;

        public PluginWithId(PluginId id, Class<?> clazz) {
            this.id = id;
            this.clazz = clazz;
        }

        AppliedPlugin asAppliedPlugin() {
            return new DefaultAppliedPlugin(id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PluginWithId that = (PluginWithId) o;

            return clazz.equals(that.clazz) && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + clazz.hashCode();
            return result;
        }
    }
}
