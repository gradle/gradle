/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.jansi;

import java.util.HashMap;
import java.util.Map;

public enum JansiOperatingSystemSupport {
    MAC_OS_X("osx"),
    LINUX("linux"),
    WINDOWS("windows");

    private final static Map<String, JansiOperatingSystemSupport> MAPPING = new HashMap<String, JansiOperatingSystemSupport>();
    private final String identifier;

    static {
        for(JansiOperatingSystemSupport osSupport : values()) {
            MAPPING.put(osSupport.identifier, osSupport);
        }
    }

    JansiOperatingSystemSupport(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public static JansiOperatingSystemSupport forIdentifier(String identifier) {
        return MAPPING.get(identifier);
    }
}
