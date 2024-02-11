/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.ide.internal.tooling.eclipse;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion;
import org.gradle.tooling.model.GradleModuleVersion;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class DefaultEclipseExternalDependency extends DefaultEclipseDependency implements Serializable {
    private final File file;
    private final File javadoc;
    private final File source;

    private final ModuleVersionIdentifier identifier;
    private final GradleModuleVersion moduleVersion;

    private final boolean resolved;
    private final DefaultEclipseComponentSelector attemptedSelector;

    private DefaultEclipseExternalDependency(File file, File javadoc, File source, ModuleVersionIdentifier identifier, boolean exported, List<DefaultClasspathAttribute> attributes, List<DefaultAccessRule> accessRules, boolean resolved, String attemptedSelector) {
        super(exported, attributes, accessRules);
        this.file = file;
        this.javadoc = javadoc;
        this.source = source;
        this.identifier = identifier;
        this.moduleVersion = (identifier == null) ? null : new DefaultGradleModuleVersion(identifier);
        this.resolved = resolved;
        this.attemptedSelector = (attemptedSelector == null) ? null : new DefaultEclipseComponentSelector(attemptedSelector);
    }

    public File getFile() {
        return file;
    }

    public File getJavadoc() {
        return javadoc;
    }

    public File getSource() {
        return source;
    }

    public ModuleVersionIdentifier getModuleVersionIdentifier() {
        return identifier;
    }

    public GradleModuleVersion getGradleModuleVersion() {
        return moduleVersion;
    }

    public boolean isResolved() {
        return resolved;
    }

    public DefaultEclipseComponentSelector getAttemptedSelector() {
        return attemptedSelector;
    }

    public static DefaultEclipseExternalDependency createResolved(File file, File javadoc, File source, ModuleVersionIdentifier identifier, boolean exported, List<DefaultClasspathAttribute> attributes, List<DefaultAccessRule> accessRules) {
        return new DefaultEclipseExternalDependency(file, javadoc, source, identifier, exported, attributes, accessRules, true, null);
    }

    public static DefaultEclipseExternalDependency createUnresolved(File file, File javadoc, File source, ModuleVersionIdentifier identifier, boolean exported, List<DefaultClasspathAttribute> attributes, List<DefaultAccessRule> accessRules, String attemptedSelector) {
        return new DefaultEclipseExternalDependency(file, javadoc, source, identifier, exported, attributes, accessRules, false, attemptedSelector);
    }

}
