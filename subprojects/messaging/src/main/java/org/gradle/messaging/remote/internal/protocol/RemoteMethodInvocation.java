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
package org.gradle.messaging.remote.internal.protocol;

import com.google.common.base.Objects;
import org.gradle.messaging.remote.internal.Message;

import java.util.Arrays;

public class RemoteMethodInvocation extends Message {
    private final Object key;
    private final Object[] arguments;

    public RemoteMethodInvocation(Object key, Object[] arguments) {
        this.key = key;
        this.arguments = arguments;
    }

    public Object getKey() {
        return key;
    }

    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        RemoteMethodInvocation other = (RemoteMethodInvocation) obj;
        return key.equals(other.key) && Arrays.equals(arguments, other.arguments);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("key", key)
                .add("arguments", Arrays.toString(arguments))
                .toString();
    }
}
