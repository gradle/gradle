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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InaccessibleObjectException;

/// [ObjectOpener] used when the instrumentation agent is unavailable: still flips `setAccessible(true)`
/// for objects whose declaring module is already open (most user-code types, plus JDK packages
/// pre-opened via `--add-opens`), but cannot open additional JDK module packages. When `setAccessible`
/// fails, the JVM's [InaccessibleObjectException] is left to propagate — matching pre-agent behavior.
class FallbackObjectOpener implements ObjectOpener {
    @Override
    public void makeAccessible(AccessibleObject accessibleObject) throws InaccessibleObjectException {
        accessibleObject.setAccessible(true);
    }
}
