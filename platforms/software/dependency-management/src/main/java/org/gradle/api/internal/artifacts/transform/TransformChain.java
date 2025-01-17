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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Action;

import javax.annotation.Nullable;

/**
 * A series of {@link TransformStep}s.
 */
public class TransformChain {
    @Nullable
    private final TransformChain init;
    private final TransformStep last;

    /**
     * @param init The initial steps of this chain, or null if this chain only contains one step.
     * @param last The last step of this chain.
     */
    public TransformChain(@Nullable TransformChain init, TransformStep last) {
        this.init = init;
        this.last = last;
    }

    /**
     * @return The initial steps of this chain, or null if this chain only contains one step.
     */
    @Nullable
    public TransformChain getInit() {
        return init;
    }

    /**
     * @return The last step of this chain.
     */
    public TransformStep getLast() {
        return last;
    }

    public boolean requiresDependencies() {
        return (init != null && init.requiresDependencies()) || last.requiresDependencies();
    }

    public String getDisplayName() {
        String lastDisplayName = last.getDisplayName();
        return init == null
            ? lastDisplayName
            : init.getDisplayName() + " -> " + lastDisplayName;
    }

    public void visitTransformSteps(Action<? super TransformStep> action) {
        if (init != null) {
            init.visitTransformSteps(action);
        }
        action.execute(last);
    }

    public int length() {
        return (init == null ? 0 : init.length()) + 1;
    }
}
