/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.logging.util;

/**
 * This class contains references to log4j-core which had a critical vulnerability,
 * see <a href="https://nvd.nist.gov/vuln/detail/CVE-2021-44228">CVE-2021-44228</a>.
 */
public class Log4jBannedVersion {
    public static final String LOG4J2_CORE_COORDINATES = "org.apache.logging.log4j:log4j-core";
    public static final String LOG4J2_CORE_VULNERABLE_VERSION_RANGE = "[2.0, 2.17.1)";
    public static final String LOG4J2_CORE_REQUIRED_VERSION = "2.17.1";
}
