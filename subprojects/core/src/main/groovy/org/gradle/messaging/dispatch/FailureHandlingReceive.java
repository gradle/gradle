/*
 * Copyright 2011 the original author or authors.
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

public class FailureHandlingReceive<T> implements Receive<T> {
    private final Receive<? extends T> receive;
    private final ReceiveFailureHandler handler;

    public FailureHandlingReceive(Receive<? extends T> receive, ReceiveFailureHandler handler) {
        this.receive = receive;
        this.handler = handler;
    }

    public T receive() {
        while (true) {
            try {
                return receive.receive();
            } catch (Throwable t) {
                handler.receiveFailed(t);
            }
        }
    }
}
