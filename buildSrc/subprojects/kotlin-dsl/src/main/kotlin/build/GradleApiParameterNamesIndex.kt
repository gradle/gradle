/*
 * Copyright 2018 the original author or authors.
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

package build


import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.model.JavaMethod

import java.io.File


fun extractParameterNamesIndexFrom(sources: Iterable<File>): Map<String, String> =
    JavaProjectBuilder()
        .let { builder -> sources.asSequence().mapNotNull { builder.addSource(it) } }
        .flatMap { it.classes.asSequence().filter { it.isPublic } }
        .flatMap { it.methods.asSequence().filter { it.isPublic && it.parameterTypes.isNotEmpty() } }
        .map { method -> fullyQualifiedSignatureOf(method) to commaSeparatedParameterNamesOf(method) }
        .toMap(linkedMapOf())


private
fun fullyQualifiedSignatureOf(method: JavaMethod): String =
    "${method.declaringClass.binaryName}.${method.name}(${signatureOf(method)})"


private
fun signatureOf(method: JavaMethod): String =
    method.parameters.joinToString(separator = ",") { p ->
        if (p.isVarArgs || p.javaClass.isArray) "${p.type.binaryName}[]"
        else p.type.binaryName
    }


private
fun commaSeparatedParameterNamesOf(method: JavaMethod) =
    method.parameters.joinToString(separator = ",") { it.name }
