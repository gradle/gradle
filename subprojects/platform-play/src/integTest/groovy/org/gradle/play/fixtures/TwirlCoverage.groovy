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

package org.gradle.play.fixtures

class TwirlCoverage {
    static final TwirlTemplateVersion LATEST = new TwirlTemplateVersion(version:"1.0.2", dependency: "com.typesafe.play:twirl-compiler_2.10:1.0.2")
    static final TwirlTemplateVersion PLAY_223_COMPLIANT = new TwirlTemplateVersion(version: "2.2.3", dependency: "com.typesafe.play:templates-compiler_2.10:2.2.3")
    static final TwirlTemplateVersion PLAY_224_COMPLIANT = new TwirlTemplateVersion(version: "2.2.4", dependency: "com.typesafe.play:templates-compiler_2.10:2.2.4")
    static final TwirlTemplateVersion PLAY_225_COMPLIANT = new TwirlTemplateVersion(version: "2.2.5", dependency: "com.typesafe.play:templates-compiler_2.10:2.2.5")
    static final TwirlTemplateVersion[] DEFAULT = [LATEST, PLAY_223_COMPLIANT];
    static final TwirlTemplateVersion[] ALL = [LATEST, PLAY_223_COMPLIANT, PLAY_224_COMPLIANT, PLAY_225_COMPLIANT];

    static class TwirlTemplateVersion{
        String dependency
        String version

        String toString() {
            version
        }
    }

}
