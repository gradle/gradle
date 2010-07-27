/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.eclipse.model.internal

import org.gradle.api.tasks.ide.eclipse.EclipseClasspath
import org.gradle.api.tasks.ide.eclipse.EclipseProject
import org.gradle.api.tasks.ide.eclipse.EclipseWtp
import org.gradle.plugins.eclipse.model.Classpath
import org.gradle.plugins.eclipse.model.Project
import org.gradle.plugins.eclipse.model.Wtp

/**
 * @author Hans Dockter
 */
class ModelFactory {
    private ClasspathFactory classpathFactory = new ClasspathFactory()
    private WtpFactory wtpFactory = new WtpFactory()

    Project createProject(EclipseProject eclipseProject) {
        File inputFile = eclipseProject.getInputFile()
        FileReader inputReader = inputFile != null && inputFile.exists() ? new FileReader(inputFile) : null
        return new Project(eclipseProject, inputReader)
    }

    Classpath createClasspath(EclipseClasspath eclipseClasspath) {
        classpathFactory.createClasspath(eclipseClasspath)
    }

    Wtp createWtp(EclipseWtp eclipseWtp) {
        wtpFactory.createWtp(eclipseWtp)
    }
}
