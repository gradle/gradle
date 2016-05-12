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
package org.gradle.launcher.daemon.protocol;

/**
 * Represents the initial message sent to the daemon, requesting that the daemon do some work.
 */
public class Command extends Message {
    private final Object identifier;
    private final byte[] token;

    public Command(Object identifier, byte[] token) {
        this.identifier = identifier;
        this.token = token;
    }

    /**
     * Returns the authentication token for this command.
     */
    public byte[] getToken() {
        return token;
    }

    /**
     * @return an id that is guaranteed to be unique in the same process
     */
    public Object getIdentifier() {
        return identifier;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + identifier + "]";
    }
}
