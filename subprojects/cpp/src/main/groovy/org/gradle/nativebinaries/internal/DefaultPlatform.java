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

package org.gradle.nativebinaries.internal;

import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.nativebinaries.Architecture;
import org.gradle.nativebinaries.OperatingSystem;
import org.gradle.nativebinaries.Platform;

public class DefaultPlatform implements Platform {
    private final NotationParser<ArchitectureInternal> archParser;
    private final NotationParser<OperatingSystem> osParser;
    private final String name;
    private Architecture architecture;
    private OperatingSystem operatingSystem;

    public DefaultPlatform(String name, NotationParser<ArchitectureInternal> archParser, NotationParser<OperatingSystem> osParser) {
        this.name = name;
        this.architecture = ArchitectureInternal.TOOL_CHAIN_DEFAULT;
        this.operatingSystem = DefaultOperatingSystem.TOOL_CHAIN_DEFAULT;
        this.archParser = archParser;
        this.osParser = osParser;
    }

    public DefaultPlatform(String name) {
        this(name, ArchitectureNotationParser.parser(), OperatingSystemNotationParser.parser());
    }

    public String getName() {
        return name;
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public void architecture(Object notation) {
        architecture = archParser.parseNotation(notation);
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public void operatingSystem(Object notation) {
        operatingSystem = osParser.parseNotation(notation);
    }
}
