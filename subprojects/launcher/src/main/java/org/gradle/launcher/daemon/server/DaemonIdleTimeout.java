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
package org.gradle.launcher.daemon.server;

import org.gradle.api.GradleException;
import org.gradle.cli.SystemPropertiesCommandLineConverter;

import java.util.Map;

public abstract class DaemonIdleTimeout {
    
    public static final int DEFAULT_IDLE_TIMEOUT = 3 * 60 * 60 * 1000;
    public static final String SYSTEM_PROPERTY_KEY = "org.gradle.daemon.idletimeout";

    public static int calculateFromPropertiesOrUseDefault(Map<?, ?> properties) {
        Object propertyValue = properties.get(SYSTEM_PROPERTY_KEY);
        if (propertyValue == null) {
            return DEFAULT_IDLE_TIMEOUT;
        } else {
            try {
                return Integer.parseInt(propertyValue.toString());
            } catch (NumberFormatException e) {
                throw new GradleException(String.format("Unable to parse %s sys property. The value should be an int but is: %s", SYSTEM_PROPERTY_KEY, propertyValue));
            }
        }
    }

    public static String toCliArg(Integer idleTimeoutMs) {
        return SystemPropertiesCommandLineConverter.toArg(SYSTEM_PROPERTY_KEY, idleTimeoutMs.toString());
    }
}
