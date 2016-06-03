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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;

import java.io.File;

public class IvyContextualArtifactPublisher implements ArtifactPublisher {
    private final ArtifactPublisher delegate;
    private final IvyContextManager ivyContextManager;

    public IvyContextualArtifactPublisher(IvyContextManager ivyContextManager, ArtifactPublisher delegate) {
        this.delegate = delegate;
        this.ivyContextManager = ivyContextManager;
    }

    @Override
    public void publish(final Iterable<? extends PublicationAwareRepository> repositories, final Module module, final Configuration configuration, final File descriptor) throws PublishException {
        ivyContextManager.withIvy(new Action<Ivy>() {
            @Override
            public void execute(Ivy ivy) {
                delegate.publish(repositories, module, configuration, descriptor);
            }
        });
    }
}
