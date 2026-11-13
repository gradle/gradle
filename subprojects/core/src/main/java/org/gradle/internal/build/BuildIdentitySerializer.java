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

package org.gradle.internal.build;

import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.Path;

import java.io.IOException;

/**
 * A thread-safe and reusable serializer for {@link BuildIdentity}.
 * <p>
 * Only the build path is written, as the rest of the identity is derived from it.
 * The path is encoded inline rather than by delegating to a {@code Path} serializer,
 * as the existing one lives in a platform this one must not depend on.
 */
public class BuildIdentitySerializer extends AbstractSerializer<BuildIdentity> {

    @Override
    public BuildIdentity read(Decoder decoder) throws IOException {
        boolean isRoot = decoder.readBoolean();
        return new BuildIdentity(isRoot ? Path.ROOT : Path.path(decoder.readString()));
    }

    @Override
    public void write(Encoder encoder, BuildIdentity value) throws IOException {
        Path buildPath = value.getBuildPath();
        boolean isRoot = buildPath.equals(Path.ROOT);
        encoder.writeBoolean(isRoot);
        if (!isRoot) {
            encoder.writeString(buildPath.asString());
        }
    }

}
