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

package org.gradle.internal.resource.local;

import org.gradle.internal.Factory;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.hash.ChecksumService;

import java.io.File;
import java.util.List;

public class AbstractLocallyAvailableResourceFinder<C> implements LocallyAvailableResourceFinder<C> {

    private final InternalTransformer<Factory<List<File>>, C> producer;
    private final ChecksumService checksumService;

    public AbstractLocallyAvailableResourceFinder(InternalTransformer<Factory<List<File>>, C> producer, ChecksumService checksumService) {
        this.producer = producer;
        this.checksumService = checksumService;
    }

    @Override
    public LocallyAvailableResourceCandidates findCandidates(C criterion) {
        return new LazyLocallyAvailableResourceCandidates(producer.transform(criterion), checksumService);
    }

    public ChecksumService getChecksumService() {
        return checksumService;
    }
}
