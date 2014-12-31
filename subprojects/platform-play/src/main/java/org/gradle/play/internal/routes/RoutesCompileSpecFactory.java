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

import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.platform.PlayPlatform;

public class RoutesCompileSpecFactory {

    public static VersionedRoutesCompileSpec create(RoutesCompileSpec spec, PlayPlatform playPlatform) {
        String playVersion = playPlatform.getPlayVersion();
        switch (PlayMajorVersion.forPlatform(playPlatform)) {
            case PLAY_2_2_X:
                return new RoutesCompileSpecV22X(spec.getSources(), spec.getDestinationDir(), spec.getAdditionalImports(), spec.getForkOptions(), spec.isJavaProject(), playVersion);
            case PLAY_2_3_X:
                return new RoutesCompileSpecV23X(spec.getSources(), spec.getDestinationDir(), spec.getAdditionalImports(), spec.isNamespaceReverseRouter(), spec.getForkOptions(), spec.isJavaProject(), playVersion);
            default:
                throw new RuntimeException("Could not create routes compile spec for Play version: " + playVersion);
        }
    }
}
