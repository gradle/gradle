/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.classpath;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;

/**
 * Provides classpaths for Gradle API and various DSL APIs (such as Kotlin DSL, Groovy DSL and so on).
 */
@NullMarked
@ServiceScope(Scope.Build.class)
public interface GradleApiClasspathProvider {

    /**
     * Returns the classpath for the Gradle API.
     */
    ClassPath getGradleApi();

    /**
     * Returns the classpath for the Kotlin DSL API.
     */
    ClassPath getGradleKotlinDslApi();

}
