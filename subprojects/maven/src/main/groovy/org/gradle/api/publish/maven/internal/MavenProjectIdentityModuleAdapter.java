/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.gradle.api.artifacts.Module;

public class MavenProjectIdentityModuleAdapter implements Module {

    private final MavenProjectIdentity mavenProjectIdentity;

    public MavenProjectIdentityModuleAdapter(MavenProjectIdentity mavenProjectIdentity) {
        this.mavenProjectIdentity = mavenProjectIdentity;
    }

    public String getGroup() {
        return mavenProjectIdentity.getGroupId();
    }

    public String getName() {
        return mavenProjectIdentity.getArtifactId();
    }

    public String getVersion() {
        return mavenProjectIdentity.getVersion();
    }

    public String getStatus() {
        return "integration"; // not used
    }
}
