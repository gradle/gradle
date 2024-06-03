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

package org.gradle.internal.service.scopes;

import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter;
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;

import java.util.List;

public class WorkerSharedBuildSessionScopeServices implements ServiceRegistrationProvider {

    @Provides
    ValueSnapshotter createValueSnapshotter(
        List<ValueSnapshotterSerializerRegistry> valueSnapshotterSerializerRegistryList,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher
    ) {
        return new DefaultValueSnapshotter(
            valueSnapshotterSerializerRegistryList,
            classLoaderHierarchyHasher
        );
    }
}
