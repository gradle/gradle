/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.publish.internal.metadata;

import com.google.gson.stream.JsonWriter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;

import java.io.IOException;
import java.io.Writer;

/**
 * <p>The Gradle module metadata file generator is responsible for generating a JSON file
 * describing module metadata. In particular, this file format is capable of handling different
 * variants with different dependency sets.</p>
 *
 * <p>Whenever you change this class, make sure you also:</p>
 *
 * <ul>
 * <li>Update the corresponding {@link GradleModuleMetadataParser module metadata parser}</li>
 * <li>Update the module metadata specification (subprojects/docs/src/docs/design/gradle-module-metadata-specification.md)</li>
 * <li>Update {@link org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataSerializer the module metadata serializer} </li>
 * <li>Add a sample for the module metadata serializer test, to make sure that serialized metadata is idempotent</li>
 * </ul>
 */
public class GradleModuleMetadataWriter {
    private final BuildInvocationScopeId buildInvocationScopeId;
    private final ChecksumService checksumService;

    public GradleModuleMetadataWriter(
        BuildInvocationScopeId buildInvocationScopeId,
        ChecksumService checksumService
    ) {
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.checksumService = checksumService;
    }

    public void writeTo(Writer writer, ModuleMetadataSpec metadata) throws IOException {

        // Write the output
        JsonWriter jsonWriter = new JsonWriter(writer);
        jsonWriter.setHtmlSafe(false);
        jsonWriter.setIndent("  ");

        new ModuleMetadataJsonWriter(
            jsonWriter,
            metadata,
            metadata.mustIncludeBuildId ? buildInvocationScopeId.getId().asString() : null,
            checksumService
        ).write();

        jsonWriter.flush();
        writer.append('\n');
    }
}
