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

// Replaced by JpmsConfiguration
// Remove once Gradle wrapper is updated and gradlebuild.unittest-and-compile.gradle.kts is using the replacement class
@Deprecated
public class GroovyJpmsConfiguration {
    /**
     * These JVM arguments should be passed to any process that will be using Groovy on Java 9+
     * Gradle accesses those packages reflectively. On Java versions 9 to 15, the users will get
     * a warning they can do nothing about. On Java 16+, strong encapsulation of JDK internals is
     * enforced and not having those arguments will result in runtime exceptions on illegal
     * reflective accesses.
     */
    public static final List<String> GROOVY_JPMS_JVM_ARGS = Collections.unmodifiableList(Arrays.asList(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        // required by PreferenceCleaningGroovySystemLoader
        "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED"
    ));
}
