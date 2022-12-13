/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.resolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.tasks.Internal

class AbstractGenerateGraphTask extends DefaultTask {
    @Internal
    File outputFile

    @Internal
    boolean buildArtifacts

    AbstractGenerateGraphTask() {
        outputs.upToDateWhen { false }
    }

    void writeResolutionResult(PrintWriter writer, ResolvedComponentResult root, Collection<ResolvedComponentResult> components, Collection<DependencyResult> dependencies) {
        writer.println("root:${formatComponent(root)}")
        components.each {
            writer.println("component:${formatComponent(it)}")
        }
        dependencies.each {
            writer.println("dependency:${it.constraint ? '[constraint]' : ''}[from:${it.from.id}][${it.requested}->${it.selected.id}]")
        }
    }

    String formatComponent(ResolvedComponentResult result) {
        String type
        if (result.id instanceof ProjectComponentIdentifier) {
            if (result.id.build.name == ':') {
                type = "project:${result.id.projectPath}"
            } else if (result.id.projectPath == ':') {
                type = "project::${result.id.build.name}"
            } else {
                type = "project::${result.id.build.name}${result.id.projectPath}"
            }
        } else if (result.id instanceof ModuleComponentIdentifier) {
            type = "module:${result.id.group}:${result.id.module}:${result.id.version},${result.id.moduleIdentifier.group}:${result.id.moduleIdentifier.name}"
        } else {
            type = "other"
        }
        String variants = result.variants.collect { variant ->
            "variant:${formatVariant(variant)}"
        }.join('@@')
        "[$type][id:${result.id}][mv:${result.moduleVersion}][reason:${formatReason(result.selectionReason)}][$variants]"
    }

    String formatVariant(ResolvedVariantResult variant) {
        return "name:${variant.displayName} attributes:${formatAttributes(variant.attributes)}"
    }

    String formatAttributes(AttributeContainer attributes) {
        attributes.keySet().collect {
            "$it.name=${attributes.getAttribute(it)}"
        }.sort().join(',')
    }

    String formatReason(ComponentSelectionReasonInternal reason) {
        def reasons = reason.descriptions.collect {
            def message
            if (it.hasCustomDescription() && it.cause != ComponentSelectionCause.REQUESTED) {
                message = "${it.cause.defaultReason}: ${it.description}"
            } else {
                message = it.description
            }
            message.readLines().join(" ")
        }.join('!!')
        return reasons
    }

}
