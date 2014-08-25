/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.Platform;
import org.gradle.nativeplatform.toolchain.GccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.TargetedPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;

import java.util.List;
import java.util.Map;

public class DefaultGccPlatformToolChain extends DefaultNamedDomainObjectSet<GccCommandLineToolConfiguration> implements TargetedPlatformToolChain<GccCommandLineToolConfiguration> {
    private final Platform platform;
    private final String name;
    private final String displayName;

    public DefaultGccPlatformToolChain(Platform platform, Map<String, GccCommandLineToolConfigurationInternal> asMap, Instantiator instantiator, String name, String displayName) {
        super(GccCommandLineToolConfiguration.class, instantiator);
        this.platform = platform;
        this.name = name;
        this.displayName = displayName;
        for (GccCommandLineToolConfigurationInternal tool : asMap.values()) {
            add(newConfiguredGccTool(tool));
        }
    }

    public Platform getPlatform() {
        return platform;
    }

    private GccCommandLineToolConfiguration newConfiguredGccTool(GccCommandLineToolConfigurationInternal defaultTool) {
        DefaultGccCommandLineToolConfiguration platformTool = getInstantiator().newInstance(DefaultGccCommandLineToolConfiguration.class, defaultTool.getName(), defaultTool.getToolType(), defaultTool.getExecutable());
        Action<List<String>> argAction = defaultTool.getArgAction();
        platformTool.withArguments(argAction);
        return platformTool;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getName() {
        return name;
    }
}