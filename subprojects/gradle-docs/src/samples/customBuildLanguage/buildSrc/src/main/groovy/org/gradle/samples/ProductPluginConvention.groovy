package org.gradle.samples

class ProductPluginConvention {
    ProductDefinition product = new ProductDefinition()
    List<File> distSrcDirs = []

    def product(Closure cl) {
        cl.delegate = product
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.call()
    }
}
