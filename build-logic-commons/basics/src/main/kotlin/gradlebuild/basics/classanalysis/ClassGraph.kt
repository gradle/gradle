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

package gradlebuild.basics.classanalysis


class ClassGraph(
    private val keepPackages: NameMatcher,
    private val unshadedPackages: NameMatcher,
    private val ignorePackages: NameMatcher,
    shadowPackage: String?
) {

    val classes: MutableMap<String, ClassDetails> = linkedMapOf()

    val entryPoints: MutableSet<ClassDetails> = linkedSetOf()

    val resources: MutableSet<String> = linkedSetOf()

    val transitiveResources: MutableSet<String> = linkedSetOf()

    private
    val shadowPackagePrefix = if (shadowPackage != null) shadowPackage.replace('.', '/') + "/" else ""

    /**
     * Returns the details for the given class.
     *
     * @param className The _original_ name of the class, not the renamed name.
     */
    operator fun get(className: String): ClassDetails {
        return classes.getOrPut(className) {
            val outputClassName = if (unshadedPackages.matches(className)) className else shadowPackagePrefix + className
            val classDetails = ClassDetails(outputClassName)
            if (keepPackages.matches(className) && !ignorePackages.matches(className)) {
                entryPoints.add(classDetails)
            }
            classDetails
        }
    }

    fun getDependencies() = classes.values.map { classDetails -> classDetails.outputClassFilename to classDetails.allDependencies.map { it.outputClassFilename } }.toMap()
}


class ClassDetails(val outputClassName: String) {
    /**
     * Was the given type present in the input?
     */
    var present: Boolean = false

    /**
     * The non-method dependencies of this type.
     */
    val dependencies: MutableSet<ClassDetails> = linkedSetOf()

    /**
     * The methods of this type.
     */
    val methods: MutableMap<String, MethodDetails> = linkedMapOf()

    val outputClassFilename
        get() = "$outputClassName.class"

    val allDependencies: Set<ClassDetails>
        get() = dependencies + methods.flatMap { it.value.dependencies.map { it.owner } }

    /**
     * Returns the method of this class with the given signature
     */
    fun method(name: String, descriptor: String): MethodDetails {
        val signature = name + descriptor
        return methods.getOrPut(signature) {
            MethodDetails(this, signature)
        }
    }
}


class MethodDetails(val owner: ClassDetails, val signature: String) {
    val dependencies: MutableSet<MethodDetails> = linkedSetOf()
}
