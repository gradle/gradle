/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeplatform.services;

import org.gradle.internal.nativeplatform.*;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.nativeplatform.jna.PosixBackedProcessEnvironment;
import org.gradle.internal.nativeplatform.jna.UnsupportedEnvironment;
import org.gradle.internal.nativeplatform.jna.WindowsProcessEnvironment;
import org.jruby.ext.posix.POSIX;

/**
 * Provides various native platform integration services.
 */
public class NativeServices extends DefaultServiceRegistry {
    protected OperatingSystem createOperatingSystem() {
        return OperatingSystem.current();
    }

    protected POSIX createPOSIX() {
        return PosixUtil.current();
    }

    protected FileSystem createFileSystem() {
        return FileSystems.getDefault();
    }

    protected ProcessEnvironment createProcessEnvironment() {
        try {
            if (OperatingSystem.current().isUnix()) {
                return new PosixBackedProcessEnvironment();
            } else if (OperatingSystem.current().isWindows()) {
                return new WindowsProcessEnvironment();
            } else {
                return new UnsupportedEnvironment();
            }
        } catch (LinkageError e) {
            // Thrown when jna cannot initialize the native stuff
            return new UnsupportedEnvironment();
        }
    }
}
