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
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.List;

public class LazyLocallyAvailableResourceCandidates implements LocallyAvailableResourceCandidates {

    private final Factory<List<File>> filesFactory;
    private final ChecksumService checksumService;
    private List<File> files;

    public LazyLocallyAvailableResourceCandidates(Factory<List<File>> filesFactory, ChecksumService checksumService) {
        this.filesFactory = filesFactory;
        this.checksumService = checksumService;
    }

    protected List<File> getFiles() {
        if (files == null) {
            files = filesFactory.create();
        }
        return files;
    }

    @Override
    public boolean isNone() {
        return getFiles().isEmpty();
    }

    @Override
    public LocallyAvailableResource findByHashValue(HashCode targetHash) {
        HashCode thisHash;
        for (File file : getFiles()) {
            thisHash = checksumService.sha1(file);
            if (thisHash.equals(targetHash)) {
                return new DefaultLocallyAvailableResource(file, thisHash);
            }
        }

        return null;
    }

}
