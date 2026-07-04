/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.management.internal;

import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

/**
 * A {@link Serializer} for {@link BuildAction} that is never invoked. The daemon message protocol requires a
 * build-action serializer to register the {@code Build} message, but the management commands (stop,
 * stop-when-idle, status) never carry a build, so this serializer is only ever registered, never used.
 */
class UnusableBuildActionSerializer implements Serializer<BuildAction> {
    @Override
    public BuildAction read(Decoder decoder) {
        throw new UnsupportedOperationException("The daemon management client does not send or receive build actions.");
    }

    @Override
    public void write(Encoder encoder, BuildAction value) {
        throw new UnsupportedOperationException("The daemon management client does not send or receive build actions.");
    }
}
