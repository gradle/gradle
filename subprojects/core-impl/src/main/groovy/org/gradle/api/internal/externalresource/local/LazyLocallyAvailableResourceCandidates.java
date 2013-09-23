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

package org.gradle.api.internal.externalresource.local;

import org.gradle.internal.Factory;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import java.io.File;
import java.util.List;

public class LazyLocallyAvailableResourceCandidates implements LocallyAvailableResourceCandidates {

    private final Factory<List<File>> filesFactory;
    private List<File> files;

    public LazyLocallyAvailableResourceCandidates(Factory<List<File>> filesFactory) {
        this.filesFactory = filesFactory;        
    }

    protected List<File> getFiles() {
        if (files == null) {
            files = filesFactory.create();
        }
        return files;
    }
    
    public boolean isNone() {
        return getFiles().isEmpty();
    }

    public LocallyAvailableResource findByHashValue(HashValue targetHash) {
        HashValue thisHash;
        for (File file : getFiles()) {
            thisHash = HashUtil.sha1(file);
            if (thisHash.equals(targetHash)) {
                return new DefaultLocallyAvailableResource(file, thisHash);
            }
        }

        return null;
    }

}
