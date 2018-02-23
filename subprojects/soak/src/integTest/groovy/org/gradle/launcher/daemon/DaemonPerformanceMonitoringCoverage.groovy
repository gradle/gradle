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

package org.gradle.launcher.daemon

import org.gradle.launcher.daemon.fixtures.FullyQualifiedGarbageCollector

import static org.gradle.launcher.daemon.fixtures.JavaGarbageCollector.*
import static org.gradle.launcher.daemon.fixtures.JdkVendor.*

class DaemonPerformanceMonitoringCoverage {
    static final def ALL_VERSIONS = [
        new FullyQualifiedGarbageCollector(vendor: IBM, version: "1.7", gc: IBM_ALL),
        // In our testing, it seems that CMS and Serial collectors behaved the same, so omitting SERIAL here
        new FullyQualifiedGarbageCollector(vendor: ORACLE, version: "1.7", gc: ORACLE_PARALLEL_CMS),
        new FullyQualifiedGarbageCollector(vendor: ORACLE, version: "1.7", gc: ORACLE_G1),
        new FullyQualifiedGarbageCollector(vendor: ORACLE, version: "1.8", gc: ORACLE_PARALLEL_CMS),
        new FullyQualifiedGarbageCollector(vendor: ORACLE, version: "1.8", gc: ORACLE_G1)
    ]
}
