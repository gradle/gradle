/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.util.VersionNumber

class GroovyCoverage {
    final static String NEWEST = GroovySystem.version

    final static String[] ALL = ['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', NEWEST]

    private final static List<VersionNumber> ALL_VERSIONS = ALL.collect { VersionNumber.parse(it) }

    final static String[] SUPPORTS_TIMESTAMP = ALL_VERSIONS.findAll {
        it >= VersionNumber.parse("2.4.6")
    }.collect {
        it.toString()
    }
}
