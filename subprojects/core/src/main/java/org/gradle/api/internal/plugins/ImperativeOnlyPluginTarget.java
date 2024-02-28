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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.declarative.dsl.model.annotations.CreatesExtension;

import javax.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.gradle.internal.Cast.uncheckedCast;

public class ImperativeOnlyPluginTarget<T extends PluginAwareInternal> implements PluginTarget {

    private final ObjectFactory objectFactory;
    private final T target;

    public ImperativeOnlyPluginTarget(ObjectFactory objectFactory, T target) {
        this.objectFactory = objectFactory;
        this.target = target;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return target.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        assert plugin != null;
        // TODO validate that the plugin accepts this kind of argument
        Plugin<T> cast = uncheckedCast(plugin);


        if (target instanceof ExtensionAware) {
            ExtensionContainer extensions = ((ExtensionAware) target).getExtensions();
            for (Method method : plugin.getClass().getMethods()) {
                CreatesExtension createsExtension = method.getAnnotation(CreatesExtension.class);
                if (createsExtension != null) {
                    Object extension;
                    try {
                        extension = method.invoke(plugin, objectFactory);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Must be able to access @CreatesExtension method", e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException("Failed to create extension", e);
                    }
                    @SuppressWarnings("unchecked")
                    Class<Object> returnType = (Class<Object>) method.getReturnType();
                    extensions.add(returnType, createsExtension.name(), extension);
                }
            }
        }

        cast.apply(target);
    }

    @Override
    public void applyRules(@Nullable String pluginId, Class<?> clazz) {
        String message = String.format("Cannot apply model rules of plugin '%s' as the target '%s' is not model rule aware", clazz.getName(), target.toString());
        throw new UnsupportedOperationException(message);
    }

    @Override
    public void applyImperativeRulesHybrid(@Nullable String pluginId, Plugin<?> plugin, Class<?> declaringClass) {
        applyRules(pluginId, declaringClass);
    }

    @Override
    public String toString() {
        return target.toString();
    }
}
