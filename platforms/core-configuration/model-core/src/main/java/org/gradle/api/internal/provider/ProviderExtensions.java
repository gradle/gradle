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

package org.gradle.api.internal.provider;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.gradle.api.provider.Provider;
import org.gradle.internal.deprecation.DeprecationLogger;

import static java.lang.String.format;


@SuppressWarnings("unused")
public class ProviderExtensions {

    /**
     * Determines the Groovy truth of a provider from the Groovy truth of its value: a provider with
     * no value is falsy, otherwise the value's own Groovy truth is used.
     *
     * <p>Extension method to support using a provider in a boolean context in Groovy.</p>
     *
     * @param self the {@link Provider}
     * @return the Groovy truth of the provider's value
     * @see <a href="https://groovy-lang.org/semantics.html#the-groovy-truth">the Groovy truth</a>
     */
    public static boolean asBoolean(Provider<?> self) {
        return evaluateAsBoolean(self, "provider");
    }

    /**
     * Determines the Groovy truth of a property from the Groovy truth of its value: a property with
     * no value is falsy, otherwise the value's own Groovy truth is used.
     *
     * <p>Extension method to support using a property in a boolean context in Groovy.</p>
     *
     * @param self the {@link Provider}
     * @return the Groovy truth of the provider's value
     * @see <a href="https://groovy-lang.org/semantics.html#the-groovy-truth">the Groovy truth</a>
     */
    public static boolean asBoolean(AbstractProperty<?, ?> self) {
        return evaluateAsBoolean(self, "property");
    }

    private static boolean evaluateAsBoolean(Provider<?> self, String type) {
        logDeprecation(type);
        return DefaultTypeTransformation.castToBoolean(self.getOrNull());
    }

    private static void logDeprecation(String type) {
        String capitalizedType = StringUtils.capitalize(type);
        DeprecationLogger.deprecateBehaviour(format("Using a `%s` where a `boolean` is expected should be avoided.", capitalizedType))
            .withAdvice(format("If evaluating this %s is intentional, do so explicitly via `get()` or `getOrNull()`.", type))
            .willBecomeAnErrorInGradle11()
            .undocumented()
            .nagUser();
    }

}
