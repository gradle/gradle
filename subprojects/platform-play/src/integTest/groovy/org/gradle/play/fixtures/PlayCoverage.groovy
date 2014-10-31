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

class PlayCoverage {
    static final PlayPlatformVersion[] DEFAULT = [new PlayPlatformVersion("2.2.3", "2.10", "templates-compiler_2.10:2.2.3"),
                                                  new PlayPlatformVersion("2.3.5", "2.10", "twirl-compiler_2.10:1.0.2")]

    public static class PlayPlatformVersion {
        private String playVersion
        private String scalaBinaryVersion
        private String templatesCompilerDependency

        PlayPlatformVersion(String playVersion, String scalaBinaryVersion, String templatesCompilerDependency){
            this.templatesCompilerDependency = templatesCompilerDependency
            this.playVersion = playVersion
            this.scalaBinaryVersion = scalaBinaryVersion
        }

        String getPlayDependency(){
            "com.typesafe.play:play_$scalaBinaryVersion:$playVersion"

        }

        String getTwirlDependency(){
            "com.typesafe.play:$templatesCompilerDependency"
        }

        String getRoutesDependency(){
            "com.typesafe.play:routes-compiler_$scalaBinaryVersion:$playVersion"
        }

        String toString(){
            "${playVersion}_${scalaBinaryVersion}";
        }
    }
}
