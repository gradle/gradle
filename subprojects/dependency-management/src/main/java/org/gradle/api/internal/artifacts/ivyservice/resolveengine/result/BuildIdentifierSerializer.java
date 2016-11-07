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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

public class BuildIdentifierSerializer implements Serializer<BuildIdentifier> {
    @Override
    public BuildIdentifier read(Decoder decoder) throws IOException {
        String buildName = decoder.readString();
        boolean current = decoder.readBoolean();
        return new DefaultBuildIdentifier(buildName, current);
    }

    @Override
    public void write(Encoder encoder, BuildIdentifier value) throws IOException {
        encoder.writeString(value.getName());
        encoder.writeBoolean(value.isCurrentBuild());
    }
}
