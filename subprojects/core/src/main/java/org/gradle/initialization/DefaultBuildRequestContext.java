/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.util.Clock;

public class DefaultBuildRequestContext implements BuildRequestContext {
    private final BuildCancellationToken token;
    private final BuildGateToken gate;
    private final BuildEventConsumer buildEventConsumer;
    private final BuildRequestMetaData metaData;

    // TODO: Wire the gate token into other levels
    public DefaultBuildRequestContext(BuildRequestMetaData metaData, BuildCancellationToken token, BuildEventConsumer buildEventConsumer) {
        this(metaData, token, new DefaultBuildGateToken(), buildEventConsumer);
    }

    public DefaultBuildRequestContext(BuildRequestMetaData metaData, BuildCancellationToken token, BuildGateToken gate, BuildEventConsumer buildEventConsumer) {
        this.metaData = metaData;
        this.token = token;
        this.gate = gate;
        this.buildEventConsumer = buildEventConsumer;
    }

    @Override
    public BuildEventConsumer getEventConsumer() {
        return buildEventConsumer;
    }

    @Override
    public BuildGateToken getGateToken() {
        return gate;
    }

    @Override
    public BuildCancellationToken getCancellationToken() {
        return token;
    }

    @Override
    public BuildClientMetaData getClient() {
        return metaData.getClient();
    }

    @Override
    public Clock getBuildTimeClock() {
        return metaData.getBuildTimeClock();
    }
}
