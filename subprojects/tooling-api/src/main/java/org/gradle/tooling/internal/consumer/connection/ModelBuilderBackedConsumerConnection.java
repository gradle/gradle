/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.ModelBuilder;

/**
 * An adapter for a {@link ModelBuilder} based provider.
 *
 * <p>Used for providers >= 1.6 and <= 1.8</p>
 */
public class ModelBuilderBackedConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ModelProducer modelProducer;
    private final ActionRunner actionRunner;

    public ModelBuilderBackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, VersionDetails.from(delegate.getMetaData().getVersion()));
        ModelBuilder builder = (ModelBuilder) delegate;
        ModelProducer modelProducer =  new ModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, builder);
        modelProducer = new GradleBuildAdapterProducer(adapter, modelProducer, this);
        modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelProducer);
        this.modelProducer = modelProducer;
        this.actionRunner = new UnsupportedActionRunner(getVersionDetails().getVersion());
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }

}
