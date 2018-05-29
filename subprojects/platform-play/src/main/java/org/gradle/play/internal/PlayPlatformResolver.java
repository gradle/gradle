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

import com.google.common.collect.ImmutableMap;
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
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.util.Map;

// TODO Resolve the JavaPlatform and ScalaPlatform from their PlatformResolvers, rather than instantiating directly
public class PlayPlatformResolver implements PlatformResolver<PlayPlatform> {
    public static final Map<String, String> LATEST_SCALA_VERSIONS =
        ImmutableMap.of("2.10", "2.10.7",
            "2.11", "2.11.12",
            "2.12", "2.12.6");

    @Override
    public Class<PlayPlatform> getType() {
        return PlayPlatform.class;
    }

    @Override
    public PlayPlatform resolve(PlatformRequirement platformRequirement) {
        if (platformRequirement instanceof PlayPlatformRequirement) {
            PlayPlatformRequirement requirement = (PlayPlatformRequirement) platformRequirement;
            return resolve(requirement.getPlatformName(), requirement.getPlayVersion(), requirement.getScalaVersion(), requirement.getJavaVersion());
        }
        String playVersion = parsePlayVersionFromPlatformName(platformRequirement.getPlatformName());
        return resolve(platformRequirement.getPlatformName(), playVersion, null, null);
    }

    private PlayPlatform resolve(String name, String playVersion, String scalaVersion, String javaVersion) {
        PlayMajorVersion playMajorVersion = PlayMajorVersion.forPlayVersion(playVersion);
        JavaPlatform javaPlatform = getJavaPlatform(javaVersion);
        ScalaPlatform scalaPlatform = getScalaPlatform(playMajorVersion, scalaVersion);
        return new DefaultPlayPlatform(name, playVersion, scalaPlatform, javaPlatform);
    }

    private String parsePlayVersionFromPlatformName(String playPlatformName) {
        if (playPlatformName.startsWith("play-")) {
            return playPlatformName.substring(5);
        }
        throw new InvalidUserDataException(String.format("Not a valid Play platform: %s.", playPlatformName));
    }

    private JavaPlatform getJavaPlatform(String preferredJavaVersion) {
        if (preferredJavaVersion != null) {
            return new DefaultJavaPlatform(JavaVersion.toVersion(preferredJavaVersion));
        }
        return new DefaultJavaPlatform(JavaVersion.current());
    }

    private ScalaPlatform getScalaPlatform(PlayMajorVersion playMajorVersion, String preferredScalaVersion) {
        String scalaVersion = GUtil.elvis(preferredScalaVersion, playMajorVersion.getDefaultScalaPlatform());
        ScalaPlatform scalaPlatform = createScalaPlatform(scalaVersion);

        playMajorVersion.validateCompatible(scalaPlatform);
        return scalaPlatform;
    }

    private ScalaPlatform createScalaPlatform(String compatibilityVersion) {
        String latestVersion = LATEST_SCALA_VERSIONS.get(compatibilityVersion);
        if (latestVersion != null) {
            return new DefaultScalaPlatform(latestVersion);
        }
        throw new InvalidUserDataException(String.format("Not a supported Scala platform identifier %s. Supported values are: [%s].", compatibilityVersion, CollectionUtils.join(", ", LATEST_SCALA_VERSIONS.keySet())));
    }

}
