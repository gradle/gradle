/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.artifacts.repositories;

import com.google.common.collect.Lists;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

import java.net.URI;
import java.util.Set;

class MavenArtifactRepositoryAdapters {

    static class ArtifactUrlsAdapter {
        @BytecodeUpgrade
        static Set<URI> getArtifactUrls(MavenArtifactRepository repository) {
            return repository.getArtifactUrls().get();
        }

        @BytecodeUpgrade
        static void setArtifactUrls(MavenArtifactRepository repository, Set<URI> urls) {
            repository.getArtifactUrls().set(urls);
        }

        @BytecodeUpgrade
        static void setArtifactUrls(MavenArtifactRepository repository, Iterable<?> urls) {
            repository.artifactUrls(Lists.newArrayList(urls).toArray());
        }
    }
}
