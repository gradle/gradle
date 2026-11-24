package org.gradle.internal.declarativedsl.language


fun Expr.asChainOrNull(): AccessChain? = (this as? NamedReference)?.asChainOrNull()


fun NamedReference.asChainOrNull(): AccessChain? =
    if (receiver == null) AccessChain(listOf(name)) else {
        val prev = when (receiver) {
            is NamedReference -> receiver.asChainOrNull()
            else -> null
        }
        if (prev != null)
            AccessChain(prev.nameParts + name)
        else null
    }
