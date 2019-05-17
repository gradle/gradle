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

package org.gradle.api.publish.internal;

import org.gradle.api.artifacts.PublishException;
import org.gradle.api.publish.Publication;

public abstract class PublishOperation implements Runnable {

    private final Publication publication;
    private final String repository;

    protected PublishOperation(Publication publication, String repository) {
        this.publication = publication;
        this.repository = repository;
    }

    protected abstract void publish() throws Exception;

    @Override
    public void run() {
        try {
            publish();
        } catch (Exception e) {
            throw new PublishException(String.format("Failed to publish publication '%s' to repository '%s'", publication.getName(), repository), e);
        }
    }
}
