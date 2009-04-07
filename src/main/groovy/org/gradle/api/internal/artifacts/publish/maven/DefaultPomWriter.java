/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.maven;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesWriter;

import java.io.PrintWriter;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultPomWriter implements PomWriter {
    private PomHeaderWriter headerWriter;
    private PomModuleIdWriter moduleIdWriter;

    private PomDependenciesWriter dependenciesWriter;

    public DefaultPomWriter(PomHeaderWriter headerWriter, PomModuleIdWriter moduleIdWriter,
                                            PomDependenciesWriter dependenciesWriter) {
        assert headerWriter != null && moduleIdWriter != null && dependenciesWriter != null;
        this.headerWriter = headerWriter;
        this.moduleIdWriter = moduleIdWriter;
        this.dependenciesWriter = dependenciesWriter;
    }

    public void convert(MavenPom pom, Set<Configuration> configurations, PrintWriter out) {
        headerWriter.convert(pom.getLicenseHeader(), out);
        moduleIdWriter.convert(pom, out);
        dependenciesWriter.convert(pom, configurations, out);
        out.println("</" + ROOT_ELEMENT_NAME + ">");
    }

    public PomHeaderWriter getHeaderWriter() {
        return headerWriter;
    }

    public void setHeaderWriter(PomHeaderWriter headerWriter) {
        this.headerWriter = headerWriter;
    }

    public PomModuleIdWriter getModuleIdWriter() {
        return moduleIdWriter;
    }

    public void setModuleIdWriter(PomModuleIdWriter moduleIdWriter) {
        this.moduleIdWriter = moduleIdWriter;
    }

    public PomDependenciesWriter getDependenciesWriter() {
        return dependenciesWriter;
    }

    public void setDependenciesWriter(PomDependenciesWriter dependenciesWriter) {
        this.dependenciesWriter = dependenciesWriter;
    }
}
