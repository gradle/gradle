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
package org.gradle.play.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.language.scala.internal.DefaultScalaPlatform;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolver;
import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.platform.PlayPlatform;

// TODO:DAZ Resolve the JavaPlatform and ScalaPlatform, rather than instantiating directly
public class PlayPlatformResolver implements PlatformResolver<PlayPlatform> {
    @Override
    public Class<PlayPlatform> getType() {
        return PlayPlatform.class;
    }

    @Override
    public PlayPlatform resolve(PlatformRequirement platformRequirement) {
        String playPlatformName = platformRequirement.getPlatformName();
        String playVersion = parsePlayVersion(playPlatformName);
        PlayMajorVersion playMajorVersion = PlayMajorVersion.forPlayVersion(playVersion);

        JavaPlatform javaPlatform = getJavaPlatform();
        ScalaPlatform scalaPlatform = getScalaPlatform(playMajorVersion);
        return new DefaultPlayPlatform(playVersion, scalaPlatform, javaPlatform);
    }

    private String parsePlayVersion(String playPlatformName) {
        if (playPlatformName.startsWith("play-")) {
            return playPlatformName.substring(5);
        }
        throw new InvalidUserDataException(String.format("Not a valid Play platform: %s.", playPlatformName));
    }

    private JavaPlatform getJavaPlatform() {
        return new DefaultJavaPlatform(JavaVersion.current());
    }

    private ScalaPlatform getScalaPlatform(PlayMajorVersion playMajorVersion) {
        switch (playMajorVersion) {
            case PLAY_2_2_X:
                return new DefaultScalaPlatform("2.10.4");
            case PLAY_2_3_X:
            default:
                return new DefaultScalaPlatform("2.11.4");
        }
    }
}
