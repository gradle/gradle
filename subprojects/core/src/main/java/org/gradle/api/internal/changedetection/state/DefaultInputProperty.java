/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.commons.lang.SerializationUtils;
import org.gradle.api.Nullable;

import java.io.NotSerializableException;
import java.io.Serializable;

public class DefaultInputProperty implements InputProperty {
    private final Object inputProperty;
    private final byte[] serializedInputProperty;
    private final HashCode hashedInputProperty;

    public DefaultInputProperty(byte[] serializedInputProperty, HashCode hash) {
        this(serializedInputProperty, hash, null);
    }

    private DefaultInputProperty(byte[] serializedInputProperty, HashCode hash, Object inputProperty) {
        this.serializedInputProperty = serializedInputProperty;
        this.hashedInputProperty = hash;
        this.inputProperty = inputProperty;
    }

    @Override
    public HashCode getHash() {
        return hashedInputProperty;
    }

    @Override
    public byte[] getSerializedBytes() {
        return serializedInputProperty;
    }

    @Nullable
    @Override
    public Object getRawValue() {
        return inputProperty;
    }

    public static DefaultInputProperty create(Object inputProperty) throws NotSerializableException {

        if (inputProperty instanceof Serializable) {
            byte[] serializedInputProperty = SerializationUtils.serialize((Serializable) inputProperty);

            Hasher hasher = Hashing.md5().newHasher();
            hasher.putBytes(serializedInputProperty);

            return new DefaultInputProperty(serializedInputProperty, hasher.hash(), inputProperty);
        } else {
            throw new NotSerializableException(inputProperty.getClass().getName());
        }
    }
}
