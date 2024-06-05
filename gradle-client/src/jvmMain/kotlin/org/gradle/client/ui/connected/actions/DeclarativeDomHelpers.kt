package org.gradle.client.ui.connected.actions

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.language.SourceData

val DocumentWithResolution.singleSoftwareTypeNode: DeclarativeDocument.DocumentNode.ElementNode
    get() = document.content.single() as DeclarativeDocument.DocumentNode.ElementNode

fun DeclarativeDocument.DocumentNode.ElementNode.childElementNode(
    name: String
): DeclarativeDocument.DocumentNode.ElementNode? =
    content.filterIsInstance<DeclarativeDocument.DocumentNode.ElementNode>().singleOrNull() { it.name == name }

fun DeclarativeDocument.DocumentNode.ElementNode.propertyValue(name: String): String? =
    (content.filterIsInstance<DeclarativeDocument.DocumentNode.PropertyNode>()
        .singleOrNull { it.name == name }
        ?.value as? DeclarativeDocument.ValueNode.LiteralValueNode)
        ?.value
        ?.toString()

fun DeclarativeDocument.DocumentNode.ElementNode.propertySourceData(name: String): SourceData? =
    content.filterIsInstance<DeclarativeDocument.DocumentNode.PropertyNode>()
        .singleOrNull { it.name == name }
        ?.sourceData
