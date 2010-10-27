package org.gradle.build

import org.gradle.api.Project

class Git {
    private final Project project

    def Git(Project project) {
        this.project = project
    }

    def checkNoModifications() {
        println 'checking for modifications'
        def stdout = new ByteArrayOutputStream()
        project.exec {
            executable = 'git'
            args = ['status', '--porcelain']
            standardOutput = stdout
        }
        if (stdout.toByteArray().length > 0) {
            throw new RuntimeException('Uncommited changes found in the source tree:\n' + stdout.toString())
        }
    }

    def tag(String tag, String message) {
        println "tagging with $tag"
        project.exec {
            executable = 'git'
            args = ['tag', '-a', tag, '-m', message]
        }
    }

    def branch(String branch) {
        println "creating branch $branch"
        project.exec {
            executable = 'git'
            args = ['branch', branch]
        }
    }
}