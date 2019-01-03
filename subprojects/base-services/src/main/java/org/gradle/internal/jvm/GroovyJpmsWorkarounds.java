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

package org.gradle.internal.jvm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GroovyJpmsWorkarounds {
    /**
     * These JVM arguments should be passed to any process that will be using Groovy on Java 9+
     * to avoid noisy illegal access warnings that the user can't do anything about.
     */
    public static final List<String> SUPPRESS_COMMON_GROOVY_WARNINGS = Collections.unmodifiableList(Arrays.asList(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED"
    ));
}
