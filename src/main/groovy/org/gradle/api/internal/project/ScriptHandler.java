package org.gradle.api.internal.project;

import groovy.lang.Script;
import org.gradle.api.Project;

/**
 * Created by IntelliJ IDEA.
 * User: hans
 * Date: Jul 1, 2008
 * Time: 10:32:38 AM
 * To change this template use File | Settings | File Templates.
 */
public interface ScriptHandler {
    Script createScript(Project project, String scriptText);

    Script writeToCache(Project project, String scriptText);

    Script loadFromCache(Project project, long lastModified);
}
