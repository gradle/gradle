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

import static org.gradle.api.JavaVersion.current

class PlayCoverage {
    static final List<String> ALL_VERSIONS = ["2.4.11", "2.5.19", "2.6.25", "2.7.9", "2.8.22"]
    static final List<String> JDK9_COMPATIBLE_VERSIONS = ["2.6.25", "2.7.9", "2.8.22"]
    static final List<String> DEFAULT = current().isJava9Compatible() ? JDK9_COMPATIBLE_VERSIONS : ALL_VERSIONS
}
