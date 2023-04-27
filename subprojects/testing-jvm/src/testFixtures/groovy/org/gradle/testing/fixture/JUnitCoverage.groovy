/*
 * Copyright 2020 the original author or authors.
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
/**
 * NEWEST is JUnit 4 series, i.e. junit:junit:4.13
 * JUPITER is JUnit Jupiter engine, i.e. org.junit.jupiter:junit-jupiter:5.7.1
 * VINTAGE is JUnit Vintage engine which supports JUnit 4 tests on top of JUnit Platform, i.e. org.junit.vintage:junit-vintage-engine:5.7.1
 */
class JUnitCoverage {
    final static String LATEST_JUNIT3_VERSION = '3.8.2'
    final static String LATEST_JUNIT4_VERSION = '4.13.2'
    final static String LATEST_JUNIT5_VERSION = '5.9.0'
    final static String LATEST_JUPITER_VERSION = LATEST_JUNIT5_VERSION
    final static String LATEST_VINTAGE_VERSION = LATEST_JUNIT5_VERSION
    final static String LATEST_PLATFORM_VERSION = '1.8.2'
    final static String LATEST_ARCHUNIT_VERSION = '0.22.0'
    final static String JUPITER = 'Jupiter:' + LATEST_JUPITER_VERSION
    final static String VINTAGE = 'Vintage:' + LATEST_VINTAGE_VERSION
    final static List<String> LARGE_COVERAGE = ['4.0', '4.4', '4.8.2', LATEST_JUNIT4_VERSION]
    final static List<String> IGNORE_ON_CLASS = ['4.4', '4.8.2', LATEST_JUNIT4_VERSION]
    final static List<String> ASSUMPTIONS = ['4.5', LATEST_JUNIT4_VERSION]
    final static List<String> CATEGORIES = ['4.8', LATEST_JUNIT4_VERSION]
    final static List<String> FILTER_JUNIT3_TESTS = [LATEST_JUNIT3_VERSION, '4.6', LATEST_JUNIT4_VERSION]
    final static List<String> JUNIT_4_LATEST = [LATEST_JUNIT4_VERSION]
    final static List<String> JUNIT_VINTAGE = [VINTAGE]
    final static List<String> JUNIT_JUPITER = [JUPITER]
    final static List<String> JUNIT_VINTAGE_JUPITER = [VINTAGE, JUPITER]
}
