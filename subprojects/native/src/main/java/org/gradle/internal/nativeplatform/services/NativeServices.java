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

import com.sun.jna.Native;
import org.gradle.internal.nativeplatform.NoOpTerminalDetector;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.TerminalDetector;
import org.gradle.internal.nativeplatform.WindowsTerminalDetector;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.nativeplatform.filesystem.FileSystems;
import org.gradle.internal.nativeplatform.jna.*;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.DefaultServiceRegistry;

/**
 * Provides various native platform integration services.
 */
public class NativeServices extends DefaultServiceRegistry {
    private static NativeServices instance = new NativeServices();

    public static NativeServices getInstance() {
        return instance;
    }

    private NativeServices() {
    }

    @Override
    public void close() {
        // Don't close
    }

    protected OperatingSystem createOperatingSystem() {
        return OperatingSystem.current();
    }

    protected FileSystem createFileSystem() {
        return FileSystems.getDefault();
    }

    protected ProcessEnvironment createProcessEnvironment() {
        try {
            if (OperatingSystem.current().isUnix()) {
                return new LibCBackedProcessEnvironment(get(LibC.class));
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

    protected TerminalDetector createTerminalDetector() {
        try {
            if (get(OperatingSystem.class).isWindows()) {
                return new WindowsTerminalDetector();
            }
            return new LibCBackedTerminalDetector(get(LibC.class));
        } catch (LinkageError e) {
            // Thrown when jna cannot initialize the native stuff
            return new NoOpTerminalDetector();
        }
    }
    
    protected LibC createLibC() {
        return (LibC) Native.loadLibrary("c", LibC.class);
    }
}
