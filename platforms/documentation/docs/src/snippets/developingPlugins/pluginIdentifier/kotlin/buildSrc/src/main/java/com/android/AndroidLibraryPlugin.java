package com.android;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class AndroidLibraryPlugin implements Plugin<Project> {
    public void apply(Project project) {
        System.out.println("Applying Android library plugin");
    }
}
