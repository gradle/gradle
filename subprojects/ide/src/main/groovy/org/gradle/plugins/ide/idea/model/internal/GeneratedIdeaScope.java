/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Set;

/**
 * An enumeration of possible mappings used to assign Idea classpath scope to Gradle dependency.
 */
public enum GeneratedIdeaScope {
    PROVIDED_TEST("PROVIDED", "TEST"),
    PROVIDED("PROVIDED"),
    COMPILE("COMPILE"),
    RUNTIME_COMPILE_CLASSPATH("PROVIDED", "RUNTIME"),
    RUNTIME_TEST_COMPILE_CLASSPATH("PROVIDED", "TEST"),
    RUNTIME_TEST("RUNTIME", "TEST"),
    RUNTIME("RUNTIME"),
    TEST("TEST"),
    COMPILE_CLASSPATH("PROVIDED");

    public final Set<String> scopes;

    GeneratedIdeaScope(String ... scopes) {
        this.scopes = Collections.unmodifiableSet(Sets.newHashSet(scopes));
    }
}
