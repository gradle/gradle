package org.gradle.client.ui.connected.actions

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode

val DeclarativeDocument.singleSoftwareTypeNode: ElementNode
    get() = content.single() as ElementNode

fun ElementNode.childElementNode(
    name: String
): ElementNode? =
    content.filterIsInstance<ElementNode>().singleOrNull() { it.name == name }

fun ElementNode.property(name: String): PropertyNode? =
    content.filterIsInstance<PropertyNode>().singleOrNull { it.name == name }