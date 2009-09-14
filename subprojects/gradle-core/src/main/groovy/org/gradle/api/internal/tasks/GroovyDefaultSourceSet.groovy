package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.FileResolver

class GroovyDefaultSourceSet extends DefaultSourceSet {
    def GroovyDefaultSourceSet(String name, FileResolver fileResolver, TaskResolver taskResolver) {
        super(name, fileResolver, taskResolver)
    }
}
