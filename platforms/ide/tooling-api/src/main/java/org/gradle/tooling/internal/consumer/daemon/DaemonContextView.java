/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.daemon;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Normalized DaemonContext metadata. Fields absent in older registry formats are
 * left null.
 */
final class DaemonContextView {
    final String uid;
    final File javaHome;
    @Nullable final Integer javaMajorVersion;   // null pre-8.8
    @Nullable final String javaVendor;          // null pre-8.10
    @Nullable final Long pid;
    @Nullable final Integer idleTimeoutMillis;
    final List<String> daemonOpts;

    DaemonContextView(
        String uid,
        File javaHome,
        @Nullable Integer javaMajorVersion,
        @Nullable String javaVendor,
        @Nullable Long pid,
        @Nullable Integer idleTimeoutMillis,
        List<String> daemonOpts
    ) {
        this.uid = uid;
        this.javaHome = javaHome;
        this.javaMajorVersion = javaMajorVersion;
        this.javaVendor = javaVendor;
        this.pid = pid;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.daemonOpts = daemonOpts;
    }
}
