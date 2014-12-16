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

package org.gradle.play.plugins;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;

/**
 * Conventional locations and names for play plugins.
 */
public class PlayPluginConfigurations {
    public static final String PLATFORM_CONFIGURATION = "playPlatform";
    public static final String COMPILE_CONFIGURATION = "play";
    public static final String TEST_COMPILE_CONFIGURATION = "playTest";

    private final Configuration playPlatform;
    private final Configuration playCompile;
    private final Configuration playTestCompile;

    public PlayPluginConfigurations(ConfigurationContainer configurations) {
        playPlatform = configurations.create(PLATFORM_CONFIGURATION);

        playCompile = configurations.create(COMPILE_CONFIGURATION);
        playCompile.extendsFrom(playPlatform);

        playTestCompile = configurations.create(TEST_COMPILE_CONFIGURATION);
        playTestCompile.extendsFrom(playCompile);
    }

    public Configuration getPlayPlatform() {
        return playPlatform;
    }

    public Configuration getPlay() {
        return playCompile;
    }

    public Configuration getPlayTest() {
        return playTestCompile;
    }
}
