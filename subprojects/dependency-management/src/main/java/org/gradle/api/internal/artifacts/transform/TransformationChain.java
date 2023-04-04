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
 * A series of {@link TransformationStep}s.
 */
public class TransformationChain implements Transformation {

    private final Transformation init;
    private final TransformationStep last;
    private final int stepsCount;

    private static final Transformation EMPTY = new Transformation() {
        @Override
        public int stepsCount() {
            return 0;
        }

        @Override
        public boolean requiresDependencies() {
            return false;
        }

        @Override
        public void visitTransformationSteps(Action<? super TransformationStep> action) {
        }

        @Override
        public String getDisplayName() {
            return "EMPTY";
        }
    };

    public TransformationChain(@Nullable Transformation init, TransformationStep last) {
        this.init = init == null ? EMPTY : init;
        this.last = last;
        this.stepsCount = this.init.stepsCount() + 1;
    }

    @Nullable
    public Transformation getInit() {
        return init == EMPTY ? null : init;
    }

    public TransformationStep getLast() {
        return last;
    }

    @Override
    public int stepsCount() {
        return stepsCount;
    }

    @Override
    public boolean requiresDependencies() {
        return init.requiresDependencies() || last.requiresDependencies();
    }

    @Override
    public String getDisplayName() {
        String lastDisplayName = last.getDisplayName();
        return init == EMPTY
            ? lastDisplayName
            : init.getDisplayName() + " -> " + lastDisplayName;
    }

    @Override
    public void visitTransformationSteps(Action<? super TransformationStep> action) {
        init.visitTransformationSteps(action);
        action.execute(last);
    }
}
