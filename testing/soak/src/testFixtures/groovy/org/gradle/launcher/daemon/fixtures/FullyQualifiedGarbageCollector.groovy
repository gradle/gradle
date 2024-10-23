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

package org.gradle.launcher.daemon.fixtures

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.daemon.JavaGarbageCollector
import org.gradle.internal.jvm.inspection.JvmVendor

class FullyQualifiedGarbageCollector implements Comparable<FullyQualifiedGarbageCollector> {
    JvmVendor.KnownJvmVendor vendor
    String version
    JavaGarbageCollector gc

    @Override
    int compareTo(FullyQualifiedGarbageCollector o) {
        int gcComparison = gc.compareTo(o.gc)
        if (gcComparison == 0) {
            int vendorComparison = vendor.compareTo(o.vendor)
            if (vendorComparison == 0) {
                return JavaVersion.toVersion(version).compareTo(JavaVersion.toVersion(o.version))
            } else {
                return vendorComparison
            }
        } else {
            return gcComparison
        }
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        FullyQualifiedGarbageCollector that = (FullyQualifiedGarbageCollector) o

        if (gc != that.gc) {
            return false
        }
        if (vendor != that.vendor) {
            return false
        }
        if (version != that.version) {
            return false
        }

        return true
    }

    int hashCode() {
        int result
        result = vendor.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + gc.hashCode()
        return result
    }


    @Override
    public String toString() {
        return "FullyQualifiedGarbageCollector{" +
            "vendor=" + vendor +
            ", version='" + version + '\'' +
            ", gc=" + gc +
            '}';
    }
}
