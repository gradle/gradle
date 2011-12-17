package org.gradle.samples

import org.gradle.api.Project

class ProductDefinition {
    String displayName
    List<Project> modules = []
    List<File> distSrcDirs = []

    def displayName(String name) {
        displayName = name
    }
    
    def module(Project project) {
        modules << project
    }
}
