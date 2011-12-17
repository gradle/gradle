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

package org.gradle.os.jna;

import org.gradle.os.OperatingSystem;
import org.gradle.os.ProcessEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses jna to update the environment variables.
 *
 * @author: Szczepan Faber, created at: 9/7/11
 */
public class NativeEnvironment {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeEnvironment.class);
    
    public static ProcessEnvironment current() {
        try {
            if (OperatingSystem.current().isUnix()) {
                return new Unix();
            } else if (OperatingSystem.current().isWindows()) {
                return new Windows();
            } else {
                LOGGER.warn("Unable to initialize native environment. Updating env variables and working directory will not be possible. Operating system: {}", OperatingSystem.current());
                return new UnsupportedEnvironment();
            }
        } catch (LinkageError e) {
            // Thrown when jna cannot initialize the native stuff
            return new UnsupportedEnvironment();
        }
    }
}
