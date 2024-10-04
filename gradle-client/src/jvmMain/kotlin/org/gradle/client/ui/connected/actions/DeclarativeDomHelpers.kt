package org.gradle.client.ui.connected.actions

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.dom.DocumentNodeContainer

val DeclarativeDocument.singleSoftwareTypeNode: ElementNode?
    get() = content.filterIsInstance<ElementNode>().singleOrNull()

fun DocumentNodeContainer.childElementNodes(name: String): List<ElementNode> = 
    content.filterIsInstance<ElementNode>().filter { it.name == name }

fun DocumentNodeContainer.property(name: String): PropertyNode? =
    content.filterIsInstance<PropertyNode>().singleOrNull { it.name == name }
