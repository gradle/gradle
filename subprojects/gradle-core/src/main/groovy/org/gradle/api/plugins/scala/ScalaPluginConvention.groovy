/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.scala

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention

class ScalaPluginConvention {
    final Project project
    List scalaSrcDirNames = []
    List scalaTestSrcDirNames = []
    List floatingScalaSrcDirs = []
    List floatingScalaTestSrcDirs = []
    String scalaDocDirName

    ScalaPluginConvention(Project project) {
        this.project = project
        scalaSrcDirNames << 'main/scala'
        scalaTestSrcDirNames << 'test/scala'
        scalaDocDirName = 'scaladoc'
    }

    List getScalaSrcDirs() {
        scalaSrcDirNames.collect {new File(javaConvention.srcRoot, it)} + floatingScalaSrcDirs
    }

    List getScalaTestSrcDirs() {
        scalaTestSrcDirNames.collect {new File(javaConvention.srcRoot, it)} + floatingScalaTestSrcDirs
    }

    File getScalaDocDir() {
        return new File(javaConvention.docsDir, scalaDocDirName)
    }

    private JavaPluginConvention getJavaConvention() {
        project.convention.plugins.java
    }

}
