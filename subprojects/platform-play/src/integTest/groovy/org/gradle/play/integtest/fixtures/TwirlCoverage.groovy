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

package org.gradle.play.integtest.fixtures

import org.gradle.api.JavaVersion
import org.gradle.play.platform.PlayPlatform
import org.gradle.play.internal.platform.DefaultPlayPlatform

class TwirlCoverage {
    static final TwirlTemplateVersion LATEST = new TwirlTemplateVersion(new DefaultPlayPlatform("2.3.6", "2.11", "1.0.2", JavaVersion.current()))
    static final TwirlTemplateVersion PLAY_233_COMPLIANT = new TwirlTemplateVersion(new DefaultPlayPlatform("2.3.5", "2.11", "1.0.2", JavaVersion.current()))
    static final TwirlTemplateVersion PLAY_223_COMPLIANT = new TwirlTemplateVersion(new DefaultPlayPlatform("2.2.3", "2.10", "2.2.3", JavaVersion.current()))

    static final TwirlTemplateVersion[] DEFAULT = [LATEST, PLAY_223_COMPLIANT];

    static class TwirlTemplateVersion{
        PlayPlatform platform

        public TwirlTemplateVersion(PlayPlatform platform){
            this.platform = platform
        }

        String toString() {
            platform.getTwirlVersion()
        }
    }

}
