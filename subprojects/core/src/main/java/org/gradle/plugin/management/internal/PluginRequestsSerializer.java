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

package org.gradle.plugin.management.internal;

import com.google.common.collect.Lists;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import java.util.List;

public class PluginRequestsSerializer extends AbstractSerializer<PluginRequests> {
    @Override
    public PluginRequests read(Decoder decoder) throws Exception {
        int requestCount = decoder.readSmallInt();
        List<PluginRequestInternal> requests = Lists.newArrayListWithCapacity(requestCount);
        for (int i = 0; i < requestCount; i++) {

            String requestingScriptDisplayName = decoder.readString();
            int requestingScriptLineNumber = decoder.readSmallInt();

            String pluginIdString = decoder.readNullableString();
            PluginId pluginId = pluginIdString == null ? null : DefaultPluginId.unvalidated(pluginIdString);
            String version = decoder.readNullableString();
            String from = decoder.readNullableString();
            boolean apply = decoder.readBoolean();

            requests.add(i, new DefaultPluginRequest(
                requestingScriptDisplayName, requestingScriptLineNumber,
                pluginId, version, from, apply, null));
        }
        return new DefaultPluginRequests(requests);
    }

    @Override
    public void write(Encoder encoder, PluginRequests requests) throws Exception {
        encoder.writeSmallInt(requests.size());
        for (PluginRequestInternal request : requests) {

            encoder.writeString(request.getRequestingScriptDisplayName());
            encoder.writeSmallInt(request.getRequestingScriptLineNumber());

            encoder.writeNullableString(request.getId() == null ? null : request.getId().getId());
            encoder.writeNullableString(request.getVersion());
            encoder.writeNullableString(request.getScript());
            encoder.writeBoolean(request.isApply());
        }
    }
}
