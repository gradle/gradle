/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugin.use.internal;

import com.google.common.collect.Lists;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.plugin.internal.PluginId;

import java.util.List;

public class PluginRequestsSerializer implements Serializer<PluginRequests> {
    @Override
    public PluginRequests read(Decoder decoder) throws Exception {
        int requestCount = decoder.readSmallInt();
        List<PluginRequest> requests = Lists.newArrayListWithCapacity(requestCount);
        for (int i = 0; i < requestCount; i++) {
            PluginId pluginId = PluginId.unvalidated(decoder.readString());
            String version = decoder.readNullableString();
            boolean apply = decoder.readBoolean();
            int lineNumber = decoder.readSmallInt();
            String scriptDisplayName = decoder.readString();

            requests.add(i, new DefaultPluginRequest(pluginId, version, apply, lineNumber, scriptDisplayName));
        }
        return new DefaultPluginRequests(requests);
    }

    @Override
    public void write(Encoder encoder, PluginRequests requests) throws Exception {
        encoder.writeSmallInt(requests.size());
        for (PluginRequest request : requests) {
            encoder.writeString(request.getId().asString());
            encoder.writeNullableString(request.getVersion());
            encoder.writeBoolean(request.isApply());
            encoder.writeSmallInt(request.getLineNumber());
            encoder.writeString(request.getScriptDisplayName());
        }
    }
}
