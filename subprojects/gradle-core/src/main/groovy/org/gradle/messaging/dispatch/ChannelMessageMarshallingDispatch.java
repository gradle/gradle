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

package org.gradle.messaging.dispatch;

import java.util.HashMap;
import java.util.Map;

public class ChannelMessageMarshallingDispatch implements Dispatch<Message> {
    private final Dispatch<Message> dispatch;
    private final Map<Object, Integer> channels = new HashMap<Object, Integer>();

    public ChannelMessageMarshallingDispatch(Dispatch<Message> dispatch) {
        this.dispatch = dispatch;
    }

    public void dispatch(Message message) {
        if (message instanceof ChannelMessage) {
            ChannelMessage channelMessage = (ChannelMessage) message;
            Object key = channelMessage.getChannel();
            Integer id = channels.get(key);
            if (id == null) {
                id = channels.size();
                channels.put(key, id);
                dispatch.dispatch(new ChannelMetaInfo(key, id));
            }
            dispatch.dispatch(new ChannelMessage(id, channelMessage.getPayload()));
        } else {
            dispatch.dispatch(message);
        }
    }
}
