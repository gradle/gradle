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

package org.gradle.api.internal.java;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.component.SoftwareComponentInternal;

import java.util.Collections;

public class WebApplication implements SoftwareComponentInternal {

    private final PublishArtifact warArtifact;

    public WebApplication(PublishArtifact warArtifact) {
        this.warArtifact = warArtifact;
    }

    public String getName() {
        return "web";
    }

    public PublishArtifactSet getArtifacts() {
        return new DefaultPublishArtifactSet("publish", new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact.class, Collections.singleton(warArtifact)));
    }

    public DependencySet getRuntimeDependencies() {
        // TODO: What are the correct dependencies for a web application?
        return new DefaultDependencySet("publish", new DefaultDomainObjectSet<Dependency>(Dependency.class));
    }
}
