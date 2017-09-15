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

import java.net.URI;
import java.util.List;

public class PluginRequestsSerializer extends AbstractSerializer<PluginRequests> {

    private static final int BINARY_PLUGIN_REQUEST_TYPE_TAG = 1;
    private static final int SCRIPT_PLUGIN_REQUEST_TYPE_TAG = 2;

    @Override
    public PluginRequests read(Decoder decoder) throws Exception {

        int requestCount = decoder.readSmallInt();
        List<PluginRequestInternal> requests = Lists.newArrayListWithCapacity(requestCount);
        for (int i = 0; i < requestCount; i++) {
            int typeTag = decoder.readSmallInt();
            switch (typeTag) {
                case BINARY_PLUGIN_REQUEST_TYPE_TAG:
                    requests.add(i, readBinaryPluginRequest(decoder));
                    break;
                case SCRIPT_PLUGIN_REQUEST_TYPE_TAG:
                    requests.add(i, readScriptPluginRequest(decoder));
                    break;
                default:
                    throw new IllegalStateException("Unknown plugin request type tag " + typeTag);
            }
        }
        return new DefaultPluginRequests(requests);
    }

    private BinaryPluginRequest readBinaryPluginRequest(Decoder decoder) throws Exception {

        String requestingScriptDisplayName = decoder.readString();
        int requestingScriptLineNumber = decoder.readSmallInt();

        String pluginIdString = decoder.readNullableString();
        PluginId pluginId = pluginIdString == null ? null : DefaultPluginId.unvalidated(pluginIdString);
        String version = decoder.readNullableString();
        boolean apply = decoder.readBoolean();

        return new BinaryPluginRequest(requestingScriptDisplayName, requestingScriptLineNumber, pluginId, version, apply, null);
    }

    private ScriptPluginRequest readScriptPluginRequest(Decoder decoder) throws Exception {

        String requestingScriptDisplayName = decoder.readString();
        int requestingScriptLineNumber = decoder.readSmallInt();

        String scriptString = decoder.readNullableString();
        URI script = scriptString == null ? null : URI.create(scriptString);

        return new ScriptPluginRequest(requestingScriptDisplayName, requestingScriptLineNumber, script);
    }

    @Override
    public void write(Encoder encoder, PluginRequests requests) throws Exception {
        encoder.writeSmallInt(requests.size());
        for (PluginRequestInternal request : requests) {
            if (request instanceof BinaryPluginRequest) {
                writeBinaryPluginRequest(encoder, (BinaryPluginRequest) request);
            } else if (request instanceof ScriptPluginRequest) {
                writeScriptPluginRequest(encoder, (ScriptPluginRequest) request);
            } else {
                throw new IllegalStateException("Unknown plugin request type " + request);
            }
        }
    }

    private void writeBinaryPluginRequest(Encoder encoder, BinaryPluginRequest request) throws Exception {

        encoder.writeSmallInt(BINARY_PLUGIN_REQUEST_TYPE_TAG);

        encoder.writeString(request.getRequestingScriptDisplayName());
        encoder.writeSmallInt(request.getRequestingScriptLineNumber());

        encoder.writeNullableString(request.getId() == null ? null : request.getId().getId());
        encoder.writeNullableString(request.getVersion());
        encoder.writeBoolean(request.isApply());
    }

    private void writeScriptPluginRequest(Encoder encoder, ScriptPluginRequest request) throws Exception {

        encoder.writeSmallInt(SCRIPT_PLUGIN_REQUEST_TYPE_TAG);

        encoder.writeString(request.getRequestingScriptDisplayName());
        encoder.writeSmallInt(request.getRequestingScriptLineNumber());

        encoder.writeNullableString(request.getScript() == null ? null : request.getScript().toString());
    }
}
