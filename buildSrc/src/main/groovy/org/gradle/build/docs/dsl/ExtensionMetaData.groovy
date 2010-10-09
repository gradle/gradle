package org.gradle.build.docs.dsl

class ExtensionMetaData {
    final String targetClass
    final Set<Map<String, String>> extensionClasses = new HashSet()

    ExtensionMetaData(String targetClass) {
        this.targetClass = targetClass
    }
    
    def void add(String plugin, String extensionClass) {
        extensionClasses << [plugin: plugin, extensionClass: extensionClass]
    }
}
