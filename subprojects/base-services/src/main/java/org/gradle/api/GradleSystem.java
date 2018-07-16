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

package org.gradle.api;

import org.gradle.api.internal.GradleProcessEnvironment;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Gradle environmental variable collection.
 *
 * @since 4.10
 */
@Incubating
public class GradleSystem {
    @Incubating
    @Nullable
    public static String getenv(String env) {
        return GradleProcessEnvironment.getenv(env);
    }

    @Incubating
    public static Map<String, String> getenv() {
        return GradleProcessEnvironment.getenv();
    }
}
