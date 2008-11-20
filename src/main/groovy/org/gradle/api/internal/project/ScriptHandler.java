package org.gradle.api.internal.project;

import groovy.lang.Script;
import org.gradle.api.Project;

public interface ScriptHandler {
    Script createScript(Project project, String scriptText);

    Script writeToCache(Project project, String scriptText);

    Script loadFromCache(Project project, long lastModified);
}
