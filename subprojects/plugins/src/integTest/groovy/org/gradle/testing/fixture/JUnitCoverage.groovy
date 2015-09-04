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

package org.gradle.testing.fixture;

class JUnitCoverage {
    final static String NEWEST = '4.12'
    final static String[] LARGE_COVERAGE = ['4.0', '4.4', '4.8.2', NEWEST]
    final static String[] IGNORE_ON_CLASS = ['4.4', '4.8.2', NEWEST]
    final static String[] ASSUMPTIONS = ['4.5', NEWEST]
    final static String[] CATEGORIES = ['4.8', NEWEST]
    final static String[] FILTER_JUNIT3_TESTS = ['3.8.1', '4.6', NEWEST]
    final static String[] LOGGING = [NEWEST]
}
