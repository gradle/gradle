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

import org.gradle.play.internal.DefaultPlayPlatform
import org.gradle.util.VersionNumber

import static org.gradle.api.JavaVersion.current

class PlayCoverage {
    static final List<VersionNumber> ALL_VERSIONS = ["2.4.11", "2.5.18", DefaultPlayPlatform.DEFAULT_PLAY_VERSION].collect { VersionNumber.parse(it) }
    static final List<VersionNumber> JDK9_COMPATIBLE_VERSIONS = [DefaultPlayPlatform.DEFAULT_PLAY_VERSION]
    static final List<String> ALL = ALL_VERSIONS.asImmutable()
    static final List<String> DEFAULT = current().isJava9Compatible() ? JDK9_COMPATIBLE_VERSIONS : ALL
}
