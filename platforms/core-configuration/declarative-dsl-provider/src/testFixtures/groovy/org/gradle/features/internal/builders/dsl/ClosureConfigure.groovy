/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders.dsl

/**
 * Utility for the "configure a Groovy DSL target with a closure" boilerplate.
 *
 * <p>Before:</p>
 * <pre>
 *   def foo = new Foo()
 *   config.delegate = foo
 *   config.resolveStrategy = Closure.DELEGATE_FIRST
 *   config.call()
 * </pre>
 *
 * <p>After:</p>
 * <pre>
 *   def foo = ClosureConfigure.configure(new Foo(), config)
 * </pre>
 */
final class ClosureConfigure {
    private ClosureConfigure() {}

    static <T> T configure(T target, Closure config) {
        config.delegate = target
        config.resolveStrategy = Closure.DELEGATE_ONLY
        config.call()
        return target
    }
}
