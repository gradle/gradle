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
package org.gradle.util

import org.gradle.os.MyFileSystem
import org.gradle.os.OperatingSystem

enum TestPrecondition {
    SWING({
        !UNSUPPORTED_OS.fulfilled
    }),
    JNA({
        !UNSUPPORTED_OS.fulfilled
    }),
    SYMLINKS({
        MyFileSystem.current().canCreateSymbolicLink()
    }),
    CASE_INSENSITIVE_FS({
        !MyFileSystem.current().caseSensitive
    }),
    FILE_PERMISSIONS({
        MAC_OS_X.fulfilled || LINUX.fulfilled
    }),
    SET_ENV_VARIABLE({
        !UNSUPPORTED_OS.fulfilled
    }),
    WORKING_DIR({
        !UNSUPPORTED_OS.fulfilled
    }),
    PROCESS_ID({
        !UNSUPPORTED_OS.fulfilled
    }),
    WINDOWS({
        OperatingSystem.current().windows
    }),
    MAC_OS_X({
        OperatingSystem.current().macOsX
    }),
    LINUX({
        OperatingSystem.current().linux
    }),
    UNSUPPORTED_OS({
        OperatingSystem.current().name.contains("unsupported")
    });

    /**
     * A predicate for testing whether the precondition is fulfilled.
     */
    private Closure predicate

    TestPrecondition(Closure predicate) {
        this.predicate = predicate
    }

    /**
     * Tells if the precondition is fulfilled.
     */
    boolean isFulfilled() {
        predicate()
    }
}

