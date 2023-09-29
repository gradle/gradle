package com.h0tk3y.kotlin.staticObjectNotation.language

fun AccessChain.dropLast(nParts: Int) = AccessChain(nameParts.dropLast(nParts), originAst)
fun AccessChain.takeFirst(nParts: Int) = AccessChain(nameParts.take(nParts), originAst)
fun AccessChain.takeLast(nParts: Int) = AccessChain(nameParts.takeLast(nParts), originAst)
fun AccessChain.afterPrefix(prefix: AccessChain): AccessChain {
    require(this.originAst === prefix.originAst)
    require(nameParts.take(prefix.nameParts.size) == prefix.nameParts)
    return AccessChain(nameParts.drop(nameParts.size), originAst)
}

fun Expr.asChainOrNull(): AccessChain? = (this as? PropertyAccess)?.asChainOrNull()

fun PropertyAccess.asChainOrNull(): AccessChain? = 
    if (receiver == null) AccessChain(listOf(name), originAst) else {
        val prev = when (receiver) {
            is PropertyAccess -> receiver.asChainOrNull()
            else -> null
        }
        if (prev != null)
            AccessChain(prev.nameParts + name, originAst)
        else null
    }