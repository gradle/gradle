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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class CompositeMutationGuard extends AbstractMutationGuard {
    private final List<MutationGuard> mutationGuards;

    public CompositeMutationGuard(List<MutationGuard> mutationGuards) {
        this.mutationGuards = mutationGuards;
    }

    @Override
    public boolean isMutationAllowed() {
        return CollectionUtils.every(mutationGuards, new Spec<MutationGuard>() {
            @Override
            public boolean isSatisfiedBy(MutationGuard mutationGuard) {
                return mutationGuard.isMutationAllowed();
            }
        });
    }

    protected <T> Action<? super T> newActionWithMutation(Action<? super T> action, final boolean allowMutationMethods) {
        for (MutationGuard mutationGuard : mutationGuards) {
            if (allowMutationMethods) {
                action = mutationGuard.withMutationEnabled(action);
            } else {
                action = mutationGuard.withMutationDisabled(action);
            }
        }

        return action;
    }

    protected void runWithMutation(Runnable runnable, final boolean allowMutationMethods) {
        for (final MutationGuard mutationGuard : mutationGuards) {
            final Runnable original = runnable;
            runnable = new Runnable() {
                @Override
                public void run() {
                    if (allowMutationMethods) {
                        mutationGuard.whileMutationEnabled(original);
                    } else {
                        mutationGuard.whileMutationDisabled(original);
                    }
                }
            };
        }

        runnable.run();
    }

    protected <I> I createWithMutation(Factory<I> factory, final boolean allowMutationMethods) {
        for (final MutationGuard mutationGuard : mutationGuards) {
            final Factory<I> original = factory;
            factory = new Factory<I>() {
                @Override
                public I create() {
                    if (allowMutationMethods) {
                        return mutationGuard.whileMutationEnabled(original);
                    }
                    return mutationGuard.whileMutationDisabled(original);
                }
            };
        }

        return factory.create();
    }
}
