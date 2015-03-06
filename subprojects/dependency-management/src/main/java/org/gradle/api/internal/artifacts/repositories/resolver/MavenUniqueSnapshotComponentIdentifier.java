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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

/**
 * A component identifier for a Maven unique snapshot module.
 */
public class MavenUniqueSnapshotComponentIdentifier extends DefaultModuleComponentIdentifier {
    private final String timestamp;

    public MavenUniqueSnapshotComponentIdentifier(String group, String module, String version, String timestamp) {
        super(group, module, version);
        this.timestamp = timestamp;
    }

    public MavenUniqueSnapshotComponentIdentifier(ModuleComponentIdentifier baseIdentifier, String timestamp) {
        super(baseIdentifier.getGroup(), baseIdentifier.getModule(), baseIdentifier.getVersion());
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && ((MavenUniqueSnapshotComponentIdentifier) o).timestamp.equals(timestamp);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + timestamp.hashCode();
    }

    @Override
    public String getDisplayName() {
        return String.format("%s:%s:%s:%s", getGroup(), getModule(), getSnapshotVersion(), timestamp);
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getSnapshotVersion() {
        return getVersion().replace(timestamp, "SNAPSHOT");
    }

    public ModuleComponentIdentifier getSnapshotComponent() {
        return DefaultModuleComponentIdentifier.newId(getGroup(), getModule(), getSnapshotVersion());
    }

    public String getTimestampedVersion() {
        return getVersion().replace("SNAPSHOT", timestamp);
    }
}
