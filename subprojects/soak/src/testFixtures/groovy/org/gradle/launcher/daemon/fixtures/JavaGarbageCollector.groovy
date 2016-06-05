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

package org.gradle.launcher.daemon.fixtures;

public enum JavaGarbageCollector {
    ORACLE_PARALLEL_CMS("-XX:+UseConcMarkSweepGC"),
    ORACLE_SERIAL9("-XX:+UseSerialGC"),
    ORACLE_G1("-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC"),
    IBM_ALL(""),
    UNKNOWN(null)

    private String jvmArgs

    JavaGarbageCollector(String jvmArgs) {
        this.jvmArgs = jvmArgs
    }

    static JavaGarbageCollector from(JdkVendor vendor, String gcName) {
        if (vendor.equals(JdkVendor.IBM)) {
            IBM_ALL
        } else if (gcName.equals("PS MarkSweep")) {
            ORACLE_PARALLEL_CMS
        } else if (gcName.equals("MarkSweepCompact")) {
            ORACLE_SERIAL9
        } else if (gcName.equals("G1 Old Generation")) {
            ORACLE_G1
        } else {
            UNKNOWN
        }
    }

    public String getJvmArgs() {
        jvmArgs
    }
}
