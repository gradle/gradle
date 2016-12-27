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

import com.google.common.base.Objects;
import com.google.common.hash.HashCode;

import java.io.NotSerializableException;

class BinaryInputProperty extends AbstractInputProperty {
    private BinaryInputProperty(HashCode hashedInputProperty) {
        super(hashedInputProperty);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        BinaryInputProperty rhs = (BinaryInputProperty) obj;
        return Objects.equal(getHashedInputProperty(), rhs.getHashedInputProperty());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getHashedInputProperty());
    }

    static BinaryInputProperty create(Object inputProperty) throws NotSerializableException {
        return new BinaryInputProperty(hash(inputProperty));
    }
}
