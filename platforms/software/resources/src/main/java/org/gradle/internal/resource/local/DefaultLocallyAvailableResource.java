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
package org.gradle.internal.resource.local;

import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.HashCode;

import java.io.File;

public class DefaultLocallyAvailableResource extends AbstractLocallyAvailableResource {
    private final File origin;

    public DefaultLocallyAvailableResource(File origin, ChecksumService checksumService) {
        super(() -> checksumService.sha1(origin));
        this.origin = origin;
    }

    public DefaultLocallyAvailableResource(File origin, HashCode sha1) {
        super(sha1);
        this.origin = origin;
    }

    @Override
    public File getFile() {
        return origin;
    }
}
