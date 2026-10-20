/*
 * Copyright 2017 the original author or authors.
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

public class CurrentBuildOperationPreservingRunnable implements Runnable {

    public static Runnable wrapIfNeeded(Runnable delegate) {
        return wrapIfNeeded(delegate, CurrentBuildOperationRef.instance());
    }

    static Runnable wrapIfNeeded(Runnable delegate, CurrentBuildOperationRef ref) {
        if (delegate instanceof CurrentBuildOperationPreservingRunnable && ((CurrentBuildOperationPreservingRunnable) delegate).ref == ref) {
            // Even if the build operation of the delegate would be different, it would override a new wrapper anyway,
            // so we can just return the delegate.
            return delegate;
        }
        BuildOperationRef buildOperation = ref.get();
        if (buildOperation == null) {
            // No build operation, so no need to wrap.
            return delegate;
        }
        return new CurrentBuildOperationPreservingRunnable(delegate, ref, buildOperation);
    }

    private final Runnable delegate;
    private final CurrentBuildOperationRef ref;
    private final BuildOperationRef buildOperation;

    private CurrentBuildOperationPreservingRunnable(Runnable delegate, CurrentBuildOperationRef ref, BuildOperationRef buildOperation) {
        this.delegate = delegate;
        this.ref = ref;
        this.buildOperation = buildOperation;
    }

    @Override
    public void run() {
        ref.with(buildOperation, delegate);
    }
}
