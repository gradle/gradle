package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

// A stand-in for a third-party plugin that is not compatible with Isolated Projects.
public class ThirdPartyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // ...
    }
}
