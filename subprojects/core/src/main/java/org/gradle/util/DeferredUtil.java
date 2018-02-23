/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.util;

import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class DeferredUtil {
    /**
     * Successively unpacks a path that may be deferred by a Callable or Factory
     * until it's resolved to null or something other than a Callable or Factory.
     */
    @Nullable
    public static Object unpack(@Nullable Object path) {
        Object current = path;
        while (current != null) {
            if (current instanceof Callable) {
                current = uncheckedCall((Callable) current);
            } else if (current instanceof Provider) {
                return ((Provider<?>) current).get();
            } else if (current instanceof Factory) {
                return ((Factory) current).create();
            } else {
                return current;
            }
        }
        return null;
    }
}
