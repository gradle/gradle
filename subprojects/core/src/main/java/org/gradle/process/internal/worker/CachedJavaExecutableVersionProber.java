/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker;

import org.gradle.api.JavaVersion;
import org.gradle.api.Nullable;
import org.gradle.process.JavaExecSpec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CachedJavaExecutableVersionProber implements JavaExecutableVersionProber {
    private final JavaExecutableVersionProber delegate;
    private final ConcurrentMap<String, OptionalValue> cache = new ConcurrentHashMap<String, OptionalValue>();

    public CachedJavaExecutableVersionProber(JavaExecutableVersionProber delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public JavaVersion probeVersion(JavaExecSpec execSpec) {
        String key = execSpec.getExecutable();
        OptionalValue optionalValue = cache.get(key);
        if (optionalValue == null) {
            optionalValue = new OptionalValue(delegate.probeVersion(execSpec));
            cache.put(key, optionalValue);
        }
        return optionalValue.value;
    }

    private static class OptionalValue {
        final JavaVersion value;

        private OptionalValue(JavaVersion value) {
            this.value = value;
        }
    }
}
