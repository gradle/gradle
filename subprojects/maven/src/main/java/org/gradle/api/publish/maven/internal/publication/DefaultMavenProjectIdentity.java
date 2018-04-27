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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

public class DefaultMavenProjectIdentity implements MavenProjectIdentity {
    private Module delegate;
    private String groupId;
    private String artifactId;
    private String version;

    public DefaultMavenProjectIdentity(Module delegate) {
        this.delegate = delegate;
    }

    public DefaultMavenProjectIdentity(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId != null ? groupId : (delegate != null ? delegate.getGroup() : null);
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId != null ? artifactId : (delegate != null ? delegate.getName() : null);
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version != null ? version : (delegate != null ? delegate.getVersion() : null);
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
