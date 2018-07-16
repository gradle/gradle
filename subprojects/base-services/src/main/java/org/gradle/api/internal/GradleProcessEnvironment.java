/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GradleProcessEnvironment {
    private static final Map<String, String> ENVS = new ConcurrentHashMap<String, String>();

    public static Map<String, String> getenv() {
        return Collections.unmodifiableMap(ENVS);
    }

    public static String getenv(String env) {
        return ENVS.get(env);
    }

    public static void unsetenv(String name) {
        ENVS.remove(name);
    }

    public static void setenv(String name, String value) {
        ENVS.put(name, value);
    }
}

