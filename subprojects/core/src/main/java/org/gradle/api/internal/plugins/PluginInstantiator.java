/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;

import java.lang.reflect.Modifier;

/**
 * An instantiator that uses the correct instantiation scheme depending on the plugin type.  This allows
 * us to use a decorated instantiator for types that need it (such as software type plugins) and continue
 * using the injected instantiator for types that do not need it (such as plugin types marked as final).
 */
@NonNullApi
public class PluginInstantiator implements Instantiator {
    private final Instantiator injectedInstantiator;
    private final Instantiator decoratedInstantiator;

    public PluginInstantiator(Instantiator injectionInstantiator, Instantiator decoratingInstantiator) {
        this.injectedInstantiator = injectionInstantiator;
        this.decoratedInstantiator = decoratingInstantiator;
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
        // If the plugin type is abstract, it likely has abstract methods that should be implemented
        // during class generation. In this case, we should use the decorated instantiator to create
        // the plugin instance.  Otherwise, we should use the injected instantiator to create the plugin
        // since some existing plugin types are known to be final.
        if (Modifier.isAbstract(type.getModifiers())) {
            return decoratedInstantiator.newInstance(type, parameters);
        } else {
            return injectedInstantiator.newInstance(type, parameters);
        }
    }
}
