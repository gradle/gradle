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

package org.gradle.internal.reflection.access;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Opens JDK (or other named-module) packages to Gradle daemon code on demand, so that deep reflective
 * access (e.g. {@code Field.setAccessible(true)}) on JDK types succeeds without a process-wide
 * {@code --add-opens} command-line flag.
 * <p>
 * Implementations dispatch to the bundled instrumentation agent when available; absent the agent,
 * implementations are a no-op and the original {@link java.lang.reflect.InaccessibleObjectException}
 * is left to propagate from the caller.
 */
@ServiceScope(Scope.Global.class)
public interface ModuleOpener {

    /**
     * Ensures the package owning {@code reflectedClass} is open to this daemon's serialization code.
     * <p>
     * No-op when the class is in an unnamed module (most user code), the same module as the implementation,
     * or when the agent is unavailable.
     *
     * @param reflectedClass the class whose declaring package should be opened
     */
    void openPackageOf(Class<?> reflectedClass);

    /**
     * Returns a no-op {@code ModuleOpener} for tests and fixtures that don't exercise module access.
     */
    static ModuleOpener noOp() {
        return new NoOpModuleOpener();
    }
}
