/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;

import java.util.HashMap;
import java.util.Map;

public class ChannelMessageUnmarshallingDispatch implements Dispatch<Object> {
    private final Dispatch<Object> dispatch;
    private final Map<Integer, Object> channels = new HashMap<Integer, Object>();

    public ChannelMessageUnmarshallingDispatch(Dispatch<Object> dispatch) {
        this.dispatch = dispatch;
    }

    public void dispatch(Object message) {
        if (message instanceof ChannelMetaInfo) {
            ChannelMetaInfo metaInfo = (ChannelMetaInfo) message;
            channels.put(metaInfo.getChannelId(), metaInfo.getChannelKey());
            return;
        }
        if (message instanceof ChannelMessage) {
            ChannelMessage channelMessage = (ChannelMessage) message;
            dispatch.dispatch(new ChannelMessage(channels.get(channelMessage.getChannel()),
                    channelMessage.getPayload()));
            return;
        }
        
        dispatch.dispatch(message);
    }
}
