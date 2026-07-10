package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.myorg.tasks.UrlVerify;

public class UrlVerifierPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        UrlVerifierExtension extension = project.getExtensions().create("verification", UrlVerifierExtension.class);
        UrlVerify verifyUrlTask = project.getTasks().create("verifyUrl", UrlVerify.class);
        verifyUrlTask.getUrl().set(extension.getUrl());
    }
}
