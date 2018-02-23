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

package org.gradle.api.internal.tasks.scala;

public final class ZincScalaCompilerUtil {
    private ZincScalaCompilerUtil() {
    }

    public static final String ZINC_CACHE_HOME_DIR_SYSTEM_PROPERTY = "org.gradle.zinc.home.dir";
    public static final String ZINC_DIR_SYSTEM_PROPERTY = "zinc.dir";
    public static final String ZINC_DIR_IGNORED_MESSAGE = "In order to guarantee parallel safe Scala compilation, Gradle does not support the '" + ZINC_DIR_SYSTEM_PROPERTY + "' system property and ignores any value provided.";
}
