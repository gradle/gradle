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

import org.gradle.messaging.dispatch.Receive;

class EndOfStreamReceive implements Receive<Object> {
    private final Receive<Object> receive;
    private boolean ended;

    public EndOfStreamReceive(Receive<Object> receive) {
        this.receive = receive;
    }

    public Object receive() {
        if (ended) {
            return null;
        }
        Object message = receive.receive();
        if (message == null) {
            ended = true;
            return new EndOfStreamEvent();
        }
        if (message instanceof EndOfStreamEvent) {
            ended = true;
        }
        return message;
    }
}
