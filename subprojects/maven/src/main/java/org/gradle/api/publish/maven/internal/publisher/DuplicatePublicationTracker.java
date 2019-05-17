/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher;

import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.Pair;

import java.util.Set;

public class DuplicatePublicationTracker {
    private final Set<Pair<Object, ModuleComponentIdentifier>> published = Sets.newHashSet();

    public void checkCanPublish(ModuleComponentIdentifier coordinates, Object repositoryId, String repositoryName) {
        Pair<Object, ModuleComponentIdentifier> key = Pair.of(repositoryId, coordinates);
        if (!published.add(key)) {
            throw new GradleException("Cannot publish multiple publications with coordinates '" + coordinates + "' to repository '" + repositoryName + "'");
        }
    }
}
