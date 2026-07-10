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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InaccessibleObjectException;

/// Enables deep reflective access on an [AccessibleObject], opening the declaring class's
/// JDK module package when needed if the bundled instrumentation agent is enabled.
///
/// Implementations fall back to a plain `setAccessible(true)` when the instrumentation
/// agent is unavailable; the original [InaccessibleObjectException] then propagates from
/// the caller, matching pre-agent behavior.
///
/// The implementations are thread-safe.
@ServiceScope(Scope.Global.class)
public interface ObjectOpener {

    /// Marks `accessibleObject` as accessible, opening the JDK module package owning its
    /// declaring class first when that's required for `setAccessible(true)` to succeed.
    ///
    /// @throws IllegalArgumentException if the module doesn't come from [ModuleLayer#boot()]
    /// @throws InaccessibleObjectException if the agent is not available and making the entity accessible fails
    void makeAccessible(AccessibleObject accessibleObject) throws InaccessibleObjectException;

    /// Returns an [ObjectOpener] that does not open any JDK module packages - equivalent to the
    /// implementation used in production when the instrumentation agent is unavailable. Suitable
    /// for tests and fixtures that don't exercise module access; still calls `setAccessible(true)`
    /// on the given object.
    static ObjectOpener agentless() {
        return new FallbackObjectOpener();
    }
}
