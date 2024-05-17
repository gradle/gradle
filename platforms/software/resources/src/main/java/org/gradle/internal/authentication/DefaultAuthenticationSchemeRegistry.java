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

package org.gradle.internal.authentication;

import com.google.common.collect.Maps;
import org.gradle.authentication.Authentication;
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Map;

public class DefaultAuthenticationSchemeRegistry implements AuthenticationSchemeRegistry {
    Map<Class<? extends Authentication>, Class<? extends Authentication>> registeredSchemes = Maps.newHashMap();

    @Override
    public <T extends Authentication> void registerScheme(Class<T> type, final Class<? extends T> implementationType) {
        registeredSchemes.put(type, implementationType);
    }

    @Override
    public <T extends Authentication> Map<Class<T>, Class<? extends T>> getRegisteredSchemes() {
        return Collections.unmodifiableMap(Cast.uncheckedNonnullCast(registeredSchemes));
    }
}
