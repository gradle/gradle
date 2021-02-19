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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readFile
import org.gradle.configurationcache.serialization.writeFile
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.IvyArtifactName
import java.io.File
import java.util.Date


/**
 * This class exists because PublishArtifactLocalArtifactMetadata is used as its own id, and so when serialized as an id causes
 * a lot of unnecessary and unserializable state to be dragged in.
 *
 * A better change would be to split an immutable id type out of PublishArtifactLocalArtifactMetadata (or reuse one of the existing
 * implementations). However, the Eclipse tooling model builder assumes that the id and metadata objects are the same and also that
 * the metadata object provides access to the backing PublishArtifact. This makes the better change too large to undertake at this point,
 * so for now just use some custom serialization that also makes assumptions and improve the Eclipse tooling model builder later.
 */
object PublishArtifactLocalArtifactMetadataCodec : Codec<PublishArtifactLocalArtifactMetadata> {
    override suspend fun WriteContext.encode(value: PublishArtifactLocalArtifactMetadata) {
        write(value.componentIdentifier)
        write(value.name)
        writeFile(value.file)
    }

    override suspend fun ReadContext.decode(): PublishArtifactLocalArtifactMetadata? {
        val componentId = read() as ComponentIdentifier
        val ivyName = read() as IvyArtifactName
        val file = readFile()
        return PublishArtifactLocalArtifactMetadata(componentId, ImmutablePublishArtifact(ivyName.name, ivyName.extension!!, ivyName.type, ivyName.classifier, file))
    }
}


private
class ImmutablePublishArtifact(
    private val name: String,
    private val extension: String,
    private val type: String,
    private val classifier: String?,
    private val file: File,
) : AbstractPublishArtifact() {

    override fun getName() = name
    override fun getExtension() = extension
    override fun getType() = type
    override fun getClassifier() = classifier
    override fun getFile() = file
    override fun getDate(): Date? = null
    override fun shouldBePublished() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutablePublishArtifact

        if (name != other.name) return false
        if (extension != other.extension) return false
        if (type != other.type) return false
        if (classifier != other.classifier) return false
        if (file != other.file) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + extension.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (classifier?.hashCode() ?: 0)
        result = 31 * result + file.hashCode()
        return result
    }
}
