/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

public class ClientStatus {

    private final String deprecationMessage;

    public ClientStatus(String deprecationMessage) {
        this.deprecationMessage = deprecationMessage;
    }

    public String getDeprecationMessage() {
        return deprecationMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClientStatus that = (ClientStatus) o;

        if (deprecationMessage != null ? !deprecationMessage.equals(that.deprecationMessage) : that.deprecationMessage != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return deprecationMessage != null ? deprecationMessage.hashCode() : 0;
    }

    public static class Serializer extends AbstractSerializer<ClientStatus> {
        public ClientStatus read(Decoder decoder) throws Exception {
            return new ClientStatus(decoder.readNullableString());
        }

        public void write(Encoder encoder, ClientStatus value) throws Exception {
            encoder.writeNullableString(value.deprecationMessage);
        }
    }

}
