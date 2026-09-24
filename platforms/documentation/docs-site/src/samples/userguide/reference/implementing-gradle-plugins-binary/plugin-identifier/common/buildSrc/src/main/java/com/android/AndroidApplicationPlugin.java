package com.android;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class AndroidApplicationPlugin implements Plugin<Project> {
    public void apply(Project project) {
        System.out.println("Applying Android application plugin");
    }
}
