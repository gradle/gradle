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

package org.gradle.play.internal.routes;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.util.VersionNumber;

public class RoutesCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(RoutesCompilerFactory.class);

    public static RoutesCompiler create(PlayPlatform playPlatform) {
        return new RoutesCompiler(createAdapter(playPlatform));
    }

    public static VersionedRoutesCompilerAdapter createAdapter(PlayPlatform playPlatform) {
        String playVersion = playPlatform.getPlayVersion();
        String scalaVersion = playPlatform.getScalaPlatform().getScalaCompatibilityVersion();
        switch (PlayMajorVersion.forPlatform(playPlatform)) {
            case PLAY_2_2_X:
                return new RoutesCompilerAdapterV22X(playVersion);
            case PLAY_2_3_X:
                return new RoutesCompilerAdapterV23X(playVersion);
            case PLAY_2_4_X:
                if (VersionNumber.parse(playVersion).getMicro() < 6) {
                    LOGGER.warn("Asked to use scala version " + scalaVersion
                        + " on play < 2.4.6. Will have to use the 2.10 routes compiler");
                    scalaVersion = "2.10";
                }
                return new RoutesCompilerAdapterV24X(playVersion, scalaVersion);
            case PLAY_2_5_X:
                return new RoutesCompilerAdapterV24X(playVersion, scalaVersion);
            default:
                throw new RuntimeException("Could not create routes compile spec for Play version: " + playVersion);
        }
    }
}
