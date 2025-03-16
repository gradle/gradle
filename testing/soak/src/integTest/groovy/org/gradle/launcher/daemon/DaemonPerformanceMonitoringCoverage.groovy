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

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.launcher.daemon.fixtures.FullyQualifiedGarbageCollector

import static org.gradle.integtests.fixtures.daemon.JavaGarbageCollector.ORACLE_G1
import static org.gradle.integtests.fixtures.daemon.JavaGarbageCollector.ORACLE_PARALLEL_CMS

class DaemonPerformanceMonitoringCoverage {
    static final def ALL_VERSIONS = [
        new FullyQualifiedGarbageCollector(vendor: JvmVendor.KnownJvmVendor.IBM, version: JavaVersion.VERSION_1_8, gc: ORACLE_PARALLEL_CMS),
        new FullyQualifiedGarbageCollector(vendor: JvmVendor.KnownJvmVendor.ORACLE, version: JavaVersion.VERSION_1_8, gc: ORACLE_G1)
    ]
}
