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

package org.gradle.process.internal.health.memory;

import net.rubygrapefruit.platform.Memory;
import net.rubygrapefruit.platform.NativeException;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.services.NativeServices;

public class NativeOsxAvailableMemory implements AvailableMemory {
    @Override
    public long get() throws UnsupportedOperationException {
        try {
            Memory memory = NativeServices.getInstance().get(Memory.class);
            return memory.getMemoryInfo().getAvailablePhysicalMemory();
        } catch (NativeException ex) {
            throw new UnsupportedOperationException("Unable to get free memory from macOS", ex);
        } catch (NativeIntegrationException ex) {
            throw new UnsupportedOperationException("Unable to get free memory from macOS", ex);
        }
    }
}
