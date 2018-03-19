package org.gradle.gradlebuild.packaging.shading

import java.io.File

class ClassGraph(
    private val keepPackages: PackagePatterns,
    val unshadedPackages: PackagePatterns,
    private val ignorePackages: PackagePatterns,
    shadowPackage: String) {

    private
    val classes: MutableMap<String, ClassDetails> = linkedMapOf()

    val entryPoints: MutableSet<ClassDetails> = linkedSetOf()
    val resources: MutableSet<ResourceDetails> = linkedSetOf()
    var manifest: ResourceDetails? = null

    internal
    val shadowPackagePrefix =
        if (shadowPackage.isEmpty()) ""
        else shadowPackage.replace('.', '/') + "/"

    fun addResource(resource: ResourceDetails) {
        resources.add(resource)
    }

    operator fun get(className: String) =
        classes.computeIfAbsent(className) {
            val outputClassName = if (unshadedPackages.matches(className)) className else shadowPackagePrefix + className
            ClassDetails(className, outputClassName).also { classDetails ->
                classes[className] = classDetails
                if (keepPackages.matches(className) && !ignorePackages.matches(className)) {
                    entryPoints.add(classDetails)
                }
            }
        }
}

class ResourceDetails(val resourceName: String, val sourceFile: File)
class PackagePatterns(givenPrefixes: Set<String>) {

    private
    val prefixes: MutableSet<String> = hashSetOf()

    private
    val names: MutableSet<String> = hashSetOf()

    init {
        givenPrefixes.map { it.replace('.', '/') }.forEach { internalName ->
            names.add(internalName)
            prefixes.add("$internalName/")
        }
    }

    fun matches(packageName: String): Boolean {
        if (names.contains(packageName)) {
            return true
        }
        for (prefix in prefixes) {
            if (packageName.startsWith(prefix)) {
                names.add(packageName)
                return true
            }
        }
        return false
    }
}

class ClassDetails(val className: String, val outputClassName: String) {
    var visited: Boolean = false
    val dependencies: MutableSet<ClassDetails> = linkedSetOf()
    val outputClassFilename
        get() = "$outputClassName.class"
}
