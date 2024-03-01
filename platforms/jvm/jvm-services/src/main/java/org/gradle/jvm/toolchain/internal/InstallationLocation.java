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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Describable;

import java.io.File;

public class InstallationLocation implements Describable {

    private final File location;

    private final String source;

    private final boolean autoDetected;

    private final boolean autoProvisioned;

    public InstallationLocation(File location, String source, boolean autoDetected) {
        this(location, source, autoDetected, false);
    }

    public InstallationLocation(File location, String source, boolean autoDetected, boolean autoProvisioned) {
        this.location = location;
        this.source = source;
        this.autoDetected = autoDetected;
        this.autoProvisioned = autoProvisioned;
    }

    public File getLocation() {
        return location;
    }

    @Override
    public String getDisplayName() {
        return "'" + location.getAbsolutePath() + "' (" + source + ")";
    }

    public String getSource() {
        return source;
    }

    /**
     * Flag for if this location was auto-detected, i.e. not explicitly configured by the user.
     *
     * This is used to lower the severity of issues related to this location.
     */
    public boolean isAutoDetected() {
        return autoDetected;
    }

    public boolean isAutoProvisioned() {
        return autoProvisioned;
    }
}
