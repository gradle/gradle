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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.IntermediateResultHandler;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.DefaultPhasedActionResultListener;
import org.gradle.tooling.internal.consumer.PhasedBuildAction;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;

import javax.annotation.Nullable;
import java.io.File;

/**
 * An adapter for {@link InternalPhasedActionConnection}.
 *
 * <p>Used for providers &gt;= 4.8.</p>
 */
public class PhasedActionAwareConsumerConnection extends ParameterAcceptingConsumerConnection {

    public PhasedActionAwareConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, modelMapping, adapter);
    }

    @Override
    public void run(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        InternalPhasedActionConnection connection = (InternalPhasedActionConnection) getDelegate();
        PhasedActionResultListener listener = new DefaultPhasedActionResultListener(getHandler(phasedBuildAction.getProjectsLoadedAction()),
            getHandler(phasedBuildAction.getBuildFinishedAction()));
        InternalPhasedAction internalPhasedAction = getPhasedAction(phasedBuildAction, operationParameters.getProjectDir(), getVersionDetails());
        try {
            connection.run(internalPhasedAction, listener, new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
        } catch (InternalBuildActionFailureException e) {
            throw new BuildActionFailureException("The supplied phased action failed with an exception.", e.getCause());
        }
    }

    @Nullable
    private static <T> IntermediateResultHandler<? super T> getHandler(@Nullable PhasedBuildAction.BuildActionWrapper<T> wrapper) {
        return wrapper == null ? null : wrapper.getHandler();
    }

    private static InternalPhasedAction getPhasedAction(PhasedBuildAction phasedBuildAction, File rootDir, VersionDetails versionDetails) {
        return new InternalPhasedActionAdapter(getAction(phasedBuildAction.getProjectsLoadedAction(), rootDir, versionDetails),
            getAction(phasedBuildAction.getBuildFinishedAction(), rootDir, versionDetails));
    }

    @Nullable
    private static <T> InternalBuildActionVersion2<T> getAction(@Nullable PhasedBuildAction.BuildActionWrapper<T> wrapper, File rootDir, VersionDetails versionDetails) {
        return wrapper == null ? null : new InternalBuildActionAdapter<T>(wrapper.getAction(), rootDir, versionDetails);
    }
}
