/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.Transformers;
import org.gradle.api.internal.artifacts.ArtifactPublisher;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.join;

public class ErrorHandlingArtifactPublisher implements ArtifactPublisher {
    private final ArtifactPublisher artifactPublisher;

    public ErrorHandlingArtifactPublisher(ArtifactPublisher artifactPublisher) {
        this.artifactPublisher = artifactPublisher;
    }

    public void publish(Module module, Set<? extends Configuration> configurations, File descriptor) {
        try {
            artifactPublisher.publish(module, configurations, descriptor);
        } catch (Throwable e) {
            String message = String.format(
                    "Could not publish configuration%s: [%s]",
                    configurations.size() > 1 ? "s" : "",
                    join(", ", collect(configurations, new TreeSet(), Transformers.name(new Configuration.Namer())))
            );
            throw new PublishException(message, e);
        }
    }
}
