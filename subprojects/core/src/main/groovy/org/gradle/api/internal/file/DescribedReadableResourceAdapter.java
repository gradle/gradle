/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.internal.DescribedReadableResource;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceDoesNotExist;
import org.gradle.api.resources.ResourceIsAFolder;

import java.io.InputStream;
import java.util.UUID;

/**
 * by Szczepan Faber, created at: 11/23/11
 */
public class DescribedReadableResourceAdapter implements DescribedReadableResource {
    private final ReadableResource resource;

    public DescribedReadableResourceAdapter(ReadableResource resource) {
        this.resource = resource;
    }

    public String getUniqueName() {
        if (resource instanceof DescribedReadableResource) {
            return ((DescribedReadableResource) resource).getUniqueName();
        }
        return getName() + "_" + UUID.randomUUID();
    }

    public String getName() {
        if (resource instanceof DescribedReadableResource) {
            return ((DescribedReadableResource) resource).getName();
        }
        return "anonymous_resource";
    }

    public InputStream read() throws ResourceDoesNotExist, ResourceIsAFolder {
        return resource.read();
    }
}
