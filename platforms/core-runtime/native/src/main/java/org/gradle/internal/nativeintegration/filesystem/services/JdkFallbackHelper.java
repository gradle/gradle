/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem.services;

import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;

public class JdkFallbackHelper {

    public static <T> T newInstanceOrFallback(String jdk7Type, ClassLoader loader, Class<? extends T> fallbackType) {
        // Use java 7 APIs, if available
        Class<?> handlerClass = null;
        if (JavaVersion.current().isJava7Compatible()) {
            try {
                handlerClass = loader.loadClass(jdk7Type);
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }
        if (handlerClass == null) {
            handlerClass = fallbackType;
        }
        try {
            return Cast.uncheckedCast(handlerClass.getConstructor().newInstance());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
