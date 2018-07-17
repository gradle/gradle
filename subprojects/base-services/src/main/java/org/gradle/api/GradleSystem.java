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
 * Gradle-managed environment variable collection. It's recommended to prefer this API over {@code java.lang.System.getnenv()}.
 *
 * @since 4.10
 */
@Incubating
public class GradleSystem {

    /**
     * Gets the value of the specified environment variable in current build.
     *
     * @param  name the name of the environment variable
     * @return the string value of the variable, or <code>null</code>
     *         if the variable is not defined in the system environment
     * @see    #getenv()
     */
    @Incubating
    @Nullable
    public static String getenv(String name) {
        return GradleProcessEnvironment.getenv(name);
    }

    /**
     *
     * Returns an unmodifiable string map view of the current system environment in current build.
     * @return the environment as a map of variable names to values
     * @see    #getenv(String)
     */
    @Incubating
    public static Map<String, String> getenv() {
        return GradleProcessEnvironment.getenv();
    }
}
