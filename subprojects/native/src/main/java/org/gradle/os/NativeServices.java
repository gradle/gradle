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
package org.gradle.os;

import org.gradle.os.jna.Unix;
import org.gradle.os.jna.UnsupportedEnvironment;
import org.gradle.os.jna.Windows;
import org.jruby.ext.posix.POSIX;

public class NativeServices {
    public <T> T get(Class<T> type) {
        if (type.isAssignableFrom(ProcessEnvironment.class)) {
            return type.cast(createProcessEnvironment());
        }
        if (type.isAssignableFrom(OperatingSystem.class)) {
            return type.cast(OperatingSystem.current());
        }
        if (type.isAssignableFrom(POSIX.class)) {
            return type.cast(PosixUtil.current());
        }
        throw new IllegalArgumentException(String.format("Do not know how to create service of type %s.", type.getSimpleName()));
    }

    protected ProcessEnvironment createProcessEnvironment() {
        try {
            if (OperatingSystem.current().isUnix()) {
                return new Unix();
            } else if (OperatingSystem.current().isWindows()) {
                return new Windows();
            } else {
                return new UnsupportedEnvironment();
            }
        } catch (LinkageError e) {
            // Thrown when jna cannot initialize the native stuff
            return new UnsupportedEnvironment();
        }
    }
}
