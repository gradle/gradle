/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("Since15")
public class DefaultBuildOperationAncestryTracker implements BuildOperationListener, BuildOperationAncestryTracker {

    private final Map<OperationIdentifier, OperationIdentifier> parents = new ConcurrentHashMap<OperationIdentifier, OperationIdentifier>();

    @Override
    public Optional<OperationIdentifier> findClosestMatchingAncestor(@Nullable OperationIdentifier id, Predicate<? super OperationIdentifier> predicate) {
        if (id == null) {
            return Optional.empty();
        }
        if (predicate.test(id)) {
            return Optional.of(id);
        }
        return findClosestMatchingAncestor(parents.get(id), predicate);
    }

    @Override
    public <T> Optional<T> findClosestExistingAncestor(@Nullable OperationIdentifier id, Function<? super OperationIdentifier, T> lookupFunction) {
        if (id == null) {
            return Optional.empty();
        }
        T value = lookupFunction.apply(id);
        if (value != null) {
            return Optional.of(value);
        }
        return findClosestExistingAncestor(parents.get(id), lookupFunction);
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getParentId() != null) {
            parents.put(buildOperation.getId(), buildOperation.getParentId());
        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        parents.remove(buildOperation.getId());
    }
}
