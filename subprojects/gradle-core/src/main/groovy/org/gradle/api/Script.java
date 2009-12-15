/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.Closure;

/**
 * <p>The base class for all scripts executed by Gradle. This is a extension to the Groovy {@code Script} class, which
 * adds in some Gradle-specific methods. Because your script will extends this class, you can use the methods and
 * properties of this class directly in your script.</p>
 *
 * <p>Generally, a {@code Script} object will have a delegate object attached to it. For example, a build script will
 * have a {@link Project} object attached to it, and an initialization script will have a {@link
 * org.gradle.api.invocation.Gradle} instance attached to it. Any property reference or method call which is not found
 * on this {@code Script} object is forwarded to the delegate object.</p>
 */
public interface Script {
    void apply(Closure closure);
}
