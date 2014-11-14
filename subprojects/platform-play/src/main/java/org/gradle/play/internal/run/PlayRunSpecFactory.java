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

package org.gradle.play.internal.run;

import org.gradle.play.platform.PlayPlatform;

public class PlayRunSpecFactory {
    public static VersionedPlayRunSpec create(PlayRunSpec spec, PlayPlatform playPlatform) {
        PlayRunVersion version = PlayRunVersion.parse(playPlatform.getPlayVersion());
        switch (version){
            case V_22X:
                return new PlayRunSpecV22X(spec.getClasspath(), spec.getProjectPath(), spec.getHttpPort(), playPlatform);
            case V_23X:
                return new PlayRunSpecV23X(spec.getClasspath(), spec.getProjectPath(), spec.getHttpPort(), playPlatform);
            default:
                throw new RuntimeException("Could not create play run spec for version: " + version);
        }
    }
}
