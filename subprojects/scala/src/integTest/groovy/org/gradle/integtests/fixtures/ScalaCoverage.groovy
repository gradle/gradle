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

package org.gradle.integtests.fixtures

import org.gradle.api.JavaVersion

class ScalaCoverage {

    static final String[] SCALA_2 = ["2.11.12", "2.12.17", "2.13.10"]
    static final String[] SCALA_3 = ["3.1.3", "3.2.1"]

    static final String[] DEFAULT = SCALA_2 + SCALA_3
    static final String[] LATEST_IN_MAJOR = [SCALA_2.last(), SCALA_3.last()]

    //to be used with getOrDefault(version, JavaVersion.VERSION_1_8)
    static final Map<String, JavaVersion> SCALA_VERSION_TO_MAX_JAVA_VERSION = ["2.13.6" : JavaVersion.VERSION_17, "2.13.1": JavaVersion.VERSION_12]

}
