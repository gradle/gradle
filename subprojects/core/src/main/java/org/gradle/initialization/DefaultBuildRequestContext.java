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

public class DefaultBuildRequestContext implements BuildRequestContext {

    private final BuildCancellationToken token;
    private final BuildEventConsumer buildEventConsumer;
    private final BuildRequestMetaData metaData;

    // TODO: Decide if we want to push the gate concept into TAPI or other entry points
    // currently, a gate is only used by continuous build and can only be controlled from within the build.
    public DefaultBuildRequestContext(BuildRequestMetaData metaData, BuildCancellationToken token, BuildEventConsumer buildEventConsumer) {
        this.metaData = metaData;
        this.token = token;
        this.buildEventConsumer = buildEventConsumer;
    }

    @Override
    public BuildEventConsumer getEventConsumer() {
        return buildEventConsumer;
    }

    @Override
    public BuildCancellationToken getCancellationToken() {
        return token;
    }

    @Override
    public BuildClientMetaData getClient() {
        return metaData.getClient();
    }

    @SuppressWarnings("deprecation")
    @Override
    public org.gradle.util.Clock getBuildTimeClock() {
        return metaData.getBuildTimeClock();
    }

    @Override
    public long getStartTime() {
        return metaData.getStartTime();
    }
}
