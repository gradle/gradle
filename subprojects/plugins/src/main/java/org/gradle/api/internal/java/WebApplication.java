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

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.Usages;

import java.util.Collections;
import java.util.Set;

public class WebApplication implements SoftwareComponentInternal {
    private final static Usage USAGE = Usages.usage("master");

    private final UsageContext webArchiveUsage = new WebArchiveUsageContext();
    private final PublishArtifact warArtifact;

    public WebApplication(PublishArtifact warArtifact) {
        this.warArtifact = warArtifact;
    }

    public String getName() {
        return "web";
    }

    public Set<UsageContext> getUsages() {
        return Collections.singleton(webArchiveUsage);
    }

    private class WebArchiveUsageContext implements UsageContext {
        public Usage getUsage() {
            return USAGE;
        }

        public Set<PublishArtifact> getArtifacts() {
            return Collections.singleton(warArtifact);
        }

        public Set<ModuleDependency> getDependencies() {
            return Collections.emptySet();
        }
    }
}
