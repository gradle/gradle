/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.JavaVersion

/**
 * NEWEST is JUnit 4 series, i.e. junit:junit:4.12
 * JUPITER is JUnit Jupiter engine, i.e. org.junit.jupiter:junit-jupiter-api:5.1.0
 * VINTAGE is JUnit Vintage engine which supports JUnit 4 tests on top of JUnit Platform, i.e. org.junit.vintage:junit-vintage-engine:5.1.0
 */
class JUnitCoverage {
    final static String NEWEST = '4.12'
    final static String PREVIEW = '4.13-beta-2'
    final static String LATEST_JUPITER_VERSION = '5.4.0'
    final static String LATEST_VINTAGE_VERSION = '5.4.0'
    final static String JUPITER = 'Jupiter:' + LATEST_JUPITER_VERSION
    final static String VINTAGE = 'Vintage:' + LATEST_VINTAGE_VERSION
    final static String[] LARGE_COVERAGE = ['4.0', '4.4', '4.8.2', NEWEST, PREVIEW]
    final static String[] IGNORE_ON_CLASS = ['4.4', '4.8.2', NEWEST, PREVIEW]
    final static String[] ASSUMPTIONS = ['4.5', NEWEST, PREVIEW]
    final static String[] CATEGORIES = ['4.8', NEWEST, PREVIEW]
    final static String[] FILTER_JUNIT3_TESTS = ['3.8.1', '4.6', NEWEST, PREVIEW]
    final static String[] JUNIT_4_LATEST = [NEWEST, PREVIEW]
    final static String[] JUNIT_VINTAGE = emptyIfJava7(VINTAGE)
    final static String[] JUNIT_VINTAGE_JUPITER = emptyIfJava7(VINTAGE, JUPITER)

    static String[] emptyIfJava7(String... versions) {
        if (JavaVersion.current().isJava8Compatible()) {
            return versions
        } else {
            return [] as String[]
        }
    }
}
