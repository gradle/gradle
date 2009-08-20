/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
class AntWar extends AbstractAntArchive {
    void execute(AntMetaArchiveParameter parameter, List classesFileSets, List libFiles, List additionalLibFileSets,
                 List webInfFileSets, File webXml) {
        assert parameter
        classesFileSets ?: []
        libFiles ?: []
        additionalLibFileSets ?: []
        Map args = [:]
        parameter.addToArgs(args)
        args.needxmlfile = 'false'
        if (webXml) { args.webxml = webXml.absolutePath }
        parameter.ant.war(args) {
            addMetaArchiveParameter(parameter, delegate)
            addResourceCollections(classesFileSets, delegate, 'classes')
            libFiles.each { File file ->
                FileSet fileSet = new FileSet(file.absoluteFile.parentFile)
                fileSet.include("$file.name")
                addResourceCollections([fileSet], delegate, 'lib')
            }
            addResourceCollections(additionalLibFileSets, delegate, 'lib')
            addResourceCollections(webInfFileSets, delegate, 'webinf')
        }
    }
}
