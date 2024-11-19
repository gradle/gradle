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

import org.apache.commons.lang3.reflect.TypeUtils;
import org.gradle.api.Plugin;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.internal.deprecation.Documentation;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

import static org.gradle.internal.Cast.uncheckedCast;

public class ImperativeOnlyPluginTarget<T extends PluginAwareInternal> implements PluginTarget {

    private final PluginTargetType targetType;
    private final T target;
    private final InternalProblems problems;

    public ImperativeOnlyPluginTarget(PluginTargetType targetType, T target, InternalProblems problems) {
        this.targetType = targetType;
        this.target = target;
        this.problems = problems;
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return target.getConfigurationTargetIdentifier();
    }

    @Override
    public void applyImperative(@Nullable String pluginId, Plugin<?> plugin) {
        // TODO validate that the plugin accepts this kind of argument
        Plugin<T> cast = uncheckedCast(plugin);
        try {
            cast.apply(target);
        } catch (ClassCastException e) {
            maybeThrowOnTargetMismatch(plugin);
            throw e;
        }
    }

    private void maybeThrowOnTargetMismatch(Plugin<?> plugin) {
        Type typeParameter = TypeUtils.getTypeArguments(plugin.getClass(), Plugin.class).get(Plugin.class.getTypeParameters()[0]);
        if (!(typeParameter instanceof Class<?>)) {
            return;
        }

        PluginTargetType actualTargetType = PluginTargetType.from((Class<?>) typeParameter);
        if (actualTargetType == null || targetType.equals(actualTargetType)) {
            return;
        }

        throw problems.getReporter()
            .throwing(spec -> {
                String message = String.format(
                    "The plugin must be applied %s, but was applied %s",
                    actualTargetType.getApplyTargetDescription(), targetType.getApplyTargetDescription()
                );

                spec.id("target-type-mismatch", "Unexpected plugin type", GradleCoreProblemGroup.pluginApplication())
                    .severity(Severity.ERROR)
                    .withException(new IllegalArgumentException(message))
                    .contextualLabel(message)
                    .documentedAt(Documentation.userManual("custom_plugins", "project_vs_settings_vs_init_plugins").toString());
            });
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
