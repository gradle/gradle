/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.nativeplatform.registry;

import java.util.prefs.Preferences;

/**
 * Provides access to the Windows registry
 */
public class WindowsRegistry {
    public static final int HKEY_CURRENT_USER = 0x80000001;
    public static final int HKEY_LOCAL_MACHINE = 0x80000002;

    public WindowsRegistryAccess get(int classId) {
        switch (classId) {
            case HKEY_CURRENT_USER:
                return new WindowsRegistryAccess(HKEY_CURRENT_USER, Preferences.userRoot());
            case HKEY_LOCAL_MACHINE:
                return new WindowsRegistryAccess(HKEY_LOCAL_MACHINE, Preferences.systemRoot());
            default:
                throw new IllegalArgumentException("Unknown registry class.");
        }
    }

}
