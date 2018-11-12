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
import org.gradle.configuration.internal.DefaultUserCodeApplicationContext;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.configuration.internal.UserCodeApplicationId;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class DefaultDomainObjectCollectionCallbackDecorator implements DomainObjectCollectionCallbackDecorator {
    private final BuildOperationExecutor buildOperationExecutor;
    private final DefaultUserCodeApplicationContext userCodeApplicationContext;

    public DefaultDomainObjectCollectionCallbackDecorator(BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext) {
        this.buildOperationExecutor = buildOperationExecutor;
        // TODO RG: discuss interface
        this.userCodeApplicationContext = (DefaultUserCodeApplicationContext) userCodeApplicationContext;
    }

    @Override
    public <T> Action<? super T> decorate(Action<? super T> action) {
        UserCodeApplicationId applicationId = userCodeApplicationContext.current();
        if (applicationId == null) {
            return action;
        }
        return new BuildOperationEmittingAction<T>(applicationId, (Action<T>) action);
    }


    private static abstract class Operation implements RunnableBuildOperation {

        private final UserCodeApplicationId applicationId;

        protected Operation(UserCodeApplicationId applicationId) {
            this.applicationId = applicationId;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor
                .displayName("Execute container callback from " + applicationId)
                .details(new ExecuteDomainObjectCollectionCallbackBuildOperationType.DetailsImpl(applicationId));
        }
    }

    private class BuildOperationEmittingAction<T> implements Action<T> {

        private final UserCodeApplicationId applicationId;
        private final Action<T> delegate;

        private BuildOperationEmittingAction(UserCodeApplicationId applicationId, Action<T> delegate) {
            this.applicationId = applicationId;
            this.delegate = delegate;
        }

        @Override
        public void execute(final T arg) {
            buildOperationExecutor.run(new Operation(applicationId) {
                @Override
                public void run(final BuildOperationContext context) {
                    userCodeApplicationContext.reapply(applicationId, new Runnable() {
                        @Override
                        public void run() {
                            delegate.execute(arg);
                        }
                    });
                    context.setResult(ExecuteDomainObjectCollectionCallbackBuildOperationType.RESULT);
                }
            });
        }
    }

}
