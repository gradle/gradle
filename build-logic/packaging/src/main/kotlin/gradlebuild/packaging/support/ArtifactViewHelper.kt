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

package gradlebuild.packaging.support

import org.gradle.api.Action
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

object ArtifactViewHelper {

    fun <R : Any> lenientProjectArtifactReselection(
        sourceConfiguration: Provider<ResolvableConfiguration>,
        attributesAction: Action<AttributeContainer>,
        @Suppress("UNCHECKED_CAST") transformer: (FileCollection) -> R = { it as R }
    ) : Provider<R> =
        sourceConfiguration.map { configuration ->
            val view = configuration.incoming.artifactView {
                withVariantReselection()
                attributesAction.execute(attributes)
                lenient(true)
                componentFilter { componentId -> componentId is ProjectComponentIdentifier }
            }
            transformer(view.files)
        }
}
