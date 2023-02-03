/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractExternalResourceAccessor implements ExternalResourceAccessor {
    @Nullable
    @Override
    public <T> T withContent(ExternalResourceName location, boolean revalidate, ExternalResource.ContentAndMetadataAction<T> action) throws ResourceException {
        ExternalResourceReadResponse response = openResource(location, revalidate);
        if (response == null) {
            return null;
        }

        try (InputStream inputStream = response.openStream();
             ExternalResourceReadResponse responseCloser = response) {
            return action.execute(inputStream, responseCloser.getMetaData());
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(location.getUri(), e);
        }
    }

    @Nullable
    protected abstract ExternalResourceReadResponse openResource(ExternalResourceName location, boolean revalidate) throws ResourceException;
}
