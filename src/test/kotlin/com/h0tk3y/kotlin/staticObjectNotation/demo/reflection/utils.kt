package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaTypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.demo.assignmentTrace
import com.h0tk3y.kotlin.staticObjectNotation.demo.printReflection
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext

fun AnalysisSchema.reflectAndPrint(code: String) {
    val resolution = resolve(code)
    val trace = assignmentTrace(resolution)
    val context = ReflectionContext(SchemaTypeRefContext(this), resolution, trace)
    val topLevel = com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect(resolution.topLevelReceiver, context)

    println(printReflection(topLevel))
}