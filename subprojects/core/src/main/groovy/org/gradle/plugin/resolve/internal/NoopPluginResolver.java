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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.initialization.ClassLoaderScope;

// Used for testing the plugins DSL
public class NoopPluginResolver implements PluginResolver {

    public PluginResolution resolve(PluginRequest pluginRequest) throws InvalidPluginRequestException {
        if (pluginRequest.getId().equals("noop")) {
            return new NoopPluginResolution();
        } else {
            return null;
        }
    }

    public String getDescriptionForNotFoundMessage() {
        return null; // hidden
    }

    public static class NoopPlugin implements Plugin<Object> {
        public void apply(Object target) {
            // do nothing
        }
    }

    private static class NoopPluginResolution implements PluginResolution {
        public Class<? extends Plugin> resolve(ClassLoaderScope classLoaderScope) {
            return new NoopPlugin().getClass();
        }
    }
}
