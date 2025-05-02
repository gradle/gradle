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

package org.gradle.api.publish.ivy.internal.publisher;

import org.apache.ivy.Ivy;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;

public class ContextualizingIvyPublisher implements IvyPublisher {
    private final IvyPublisher ivyPublisher;
    private final IvyContextManager ivyContextManager;

    public ContextualizingIvyPublisher(IvyPublisher ivyPublisher, IvyContextManager ivyContextManager) {
        this.ivyPublisher = ivyPublisher;
        this.ivyContextManager = ivyContextManager;
    }

    @Override
    public void publish(final IvyNormalizedPublication publication, final IvyArtifactRepository repository) {
        ivyContextManager.withIvy(new Action<Ivy>() {
            @Override
            public void execute(Ivy ivy) {
                ivyPublisher.publish(publication, repository);
            }
        });
    }
}
