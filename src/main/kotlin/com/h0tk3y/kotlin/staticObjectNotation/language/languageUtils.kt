package com.h0tk3y.kotlin.staticObjectNotation.language

fun Expr.asChainOrNull(): AccessChain? = (this as? PropertyAccess)?.asChainOrNull()

fun PropertyAccess.asChainOrNull(): AccessChain? =
    if (receiver == null) AccessChain(listOf(name), sourceData) else {
        val prev = when (receiver) {
            is PropertyAccess -> receiver.asChainOrNull()
            else -> null
        }
        if (prev != null)
            AccessChain(prev.nameParts + name, sourceData)
        else null
    }
