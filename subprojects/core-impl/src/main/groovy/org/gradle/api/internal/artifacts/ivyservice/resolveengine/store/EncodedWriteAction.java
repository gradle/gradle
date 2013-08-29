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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store;

import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.messaging.serialize.FlushableEncoder;
import org.gradle.messaging.serialize.OutputStreamBackedEncoder;

import java.io.DataOutputStream;
import java.io.IOException;

public abstract class EncodedWriteAction implements BinaryStore.WriteAction {
    public final void write(DataOutputStream output) throws IOException {
        write(new OutputStreamBackedEncoder(output));
    }

    public abstract void write(FlushableEncoder encoder) throws IOException;
}
