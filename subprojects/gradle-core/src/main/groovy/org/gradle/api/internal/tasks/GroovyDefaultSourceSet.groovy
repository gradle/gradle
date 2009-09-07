package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.FileResolver

class GroovyDefaultSourceSet extends DefaultSourceSet {
    def GroovyDefaultSourceSet(String name, FileResolver resolver) {
        super(name, resolver)
    }
}
