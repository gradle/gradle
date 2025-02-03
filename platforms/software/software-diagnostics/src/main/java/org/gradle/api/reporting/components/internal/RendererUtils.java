/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.components.internal;

import org.gradle.api.Named;

import java.lang.reflect.Method;

public class RendererUtils {
    /**
     * Converts a value into a string. Never returns {@code null}.
     *
     * <p>Rules used to determine the string value:</p>
     *
     * <ul>
     *     <li>the {@code null} value is converted to the string {@code "null"},</li>
     *     <li>if the value has a type that overrides {@link Object#toString()} then it is converted to {@code value.toString()},</li>
     *     <li>if the value's type implements {@link Named} then it is converted to {@code value.getName()},</li>
     *     <li>otherwise the return value of {@link Object#toString()} is used.</li>
     * </ul>
     *
     * <p>The method returns the converted value, unless it is {@code null}, in which case the string {@code "null"} is returned instead.</p>
     */
    public static String displayValueOf(Object value) {
        String result = null;
        if (value != null) {
            boolean hasCustomToString;
            try {
                Method toString = value.getClass().getMethod("toString");
                hasCustomToString = !toString.getDeclaringClass().equals(Object.class);
            } catch (NoSuchMethodException ignore) {
                hasCustomToString = false;
            }

            if (!hasCustomToString && Named.class.isAssignableFrom(value.getClass())) {
                result = ((Named) value).getName();
            } else {
                result = value.toString();
            }
        }
        if (result == null) {
            result = "null";
        }
        return result;
    }
}
