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

package org.gradle.play.internal.platform;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.util.VersionNumber;

import java.util.List;

public enum PlayMajorVersion {
    PLAY_2_2_X("2.2.x", "2.10"),
    PLAY_2_3_X("2.3.x", "2.11", "2.10"),
    PLAY_2_4_X("2.4.x", "2.11", "2.10"),
    PLAY_2_5_X("2.5.x", "2.11");

    private final String name;
    private final List<String> compatibleScalaVersions;

    PlayMajorVersion(String name, String... compatibleScalaVersions) {
        this.name = name;
        this.compatibleScalaVersions = Lists.newArrayList(compatibleScalaVersions);
    }

    public void validateCompatible(ScalaPlatform scalaPlatform) {
        if (!compatibleScalaVersions.contains(scalaPlatform.getScalaCompatibilityVersion())) {
            throw new InvalidUserDataException(
                    String.format("Play versions %s are not compatible with Scala platform %s. Compatible Scala platforms are %s.",
                            name, scalaPlatform.getScalaCompatibilityVersion(), compatibleScalaVersions));
        }
    }

    public String getDefaultScalaPlatform() {
        return compatibleScalaVersions.get(0);
    }

    public static PlayMajorVersion forPlatform(PlayPlatform targetPlatform) {
        String playVersion = targetPlatform.getPlayVersion();
        return forPlayVersion(playVersion);
    }

    public static PlayMajorVersion forPlayVersion(String playVersion) {
        VersionNumber versionNumber = VersionNumber.parse(playVersion);
        if (versionNumber.getMajor() == 2) {
            switch (versionNumber.getMinor()) {
                case 2:
                    return PlayMajorVersion.PLAY_2_2_X;
                case 3:
                    return PlayMajorVersion.PLAY_2_3_X;
                case 4:
                    return PlayMajorVersion.PLAY_2_4_X;
                case 5:
                    return PlayMajorVersion.PLAY_2_5_X;
                default:
                    throw invalidVersion(playVersion);
            }
        }
        throw invalidVersion(playVersion);
    }

    private static InvalidUserDataException invalidVersion(String playVersion) {
        return new InvalidUserDataException(String.format("Not a supported Play version: %s. "
            + "This plugin is compatible with: [2.5.x, 2.4.x, 2.3.x, 2.2.x].", playVersion));
    }

}
