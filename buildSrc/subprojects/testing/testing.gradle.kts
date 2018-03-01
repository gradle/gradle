dependencies {
    compile(project(":build"))
    compile("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2")

    components {
        withModule("net.sourceforge.nekohtml:nekohtml") {
            allVariants {
                // Xerces on the runtime classpath is breaking some of our doc tasks
                withDependencies { removeAll { it.group == "xerces" } }
            }
        }
    }
}
