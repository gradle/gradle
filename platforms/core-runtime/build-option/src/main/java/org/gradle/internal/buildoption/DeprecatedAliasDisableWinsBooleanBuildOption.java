/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildoption;

import java.util.Map;

/**
 * A boolean build option with a deprecated property alias where an explicit disable
 * through either property name wins: when both names are set, the option is enabled
 * only if both values are true.
 *
 * <p>This keeps opt-outs reliable while a renamed property is deprecated: tooling that
 * still disables a feature under the deprecated name must keep working even when the
 * build enables the feature under the new name.
 *
 * <p>The downside is that this rule ignores source precedence: a disable from a
 * lower-precedence source (e.g. a {@code gradle.properties} file) overrides an enable
 * from a higher-precedence source (e.g. a command-line {@code -D} argument) when the
 * two use different names. Only the command-line flags can override a disable.
 *
 * <p>This is a stop-gap rather than the desired end state. The proper solution is to
 * make the deprecated name an actual alias of the main option, resolved against it
 * within each property source, instead of after all sources have been merged.
 * See <a href="https://github.com/gradle/gradle/issues/38598">#38598</a>.
 *
 * @param <T> the type of object the option value is applied to
 */
public abstract class DeprecatedAliasDisableWinsBooleanBuildOption<T> extends BooleanBuildOption<T> {

    public DeprecatedAliasDisableWinsBooleanBuildOption(
        String property,
        String deprecatedProperty,
        BooleanCommandLineOptionConfiguration... commandLineOptionConfigurations
    ) {
        super(property, deprecatedProperty, commandLineOptionConfigurations);
    }

    @Override
    protected OptionValue<String> getFromProperties(Map<String, String> properties) {
        String value = properties.get(property);
        String deprecatedValue = properties.get(deprecatedProperty);
        if (value != null && deprecatedValue != null
            && BooleanOptionUtil.isTrue(value) && !BooleanOptionUtil.isTrue(deprecatedValue)) {
            // The deprecated name carries an explicit disable that the main property would otherwise mask
            return new OptionValue<String>(deprecatedValue, Origin.forGradleProperty(deprecatedProperty));
        }
        return super.getFromProperties(properties);
    }
}
