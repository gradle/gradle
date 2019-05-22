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
package org.gradle.api.tasks

import org.apache.tools.ant.taskdefs.MatchingTask
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.types.Resource
import org.apache.tools.ant.types.ResourceCollection
import org.gradle.api.AntBuilder
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.DefaultAntBuilder
import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.*
import org.gradle.api.internal.file.collections.MinimalFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter

class AntBuilderAwareUtil {

    static def assertSetContains(FileCollection set, Set<String> filenames) {
        assertSetContains(set, filenames, [FileCollection.AntType.ResourceCollection])
    }

    static def assertSetContains(FileCollection set, Set<String> filenames, Iterable<FileCollection.AntType> types, boolean generic = true) {
        AntBuilder ant = new DefaultAntBuilder(null, null)
        ant.antProject.addTaskDefinition('test', FileListTask)

        if (generic) {
            FileListTask task = ant.test {
                set.addToAntBuilder(ant, null)
            }
            assertThat(task.filenames, equalTo(filenames))
        }

        types.each {FileCollection.AntType type ->
            FileListTask task = ant.test {
                set.addToAntBuilder(ant, type == FileCollection.AntType.ResourceCollection ? null : type.toString().toLowerCase(), type)
            }

            assertThat("Unexpected FileCollection contents for type $type", task.filenames, equalTo(filenames))
        }
    }

    static def assertSetContains(FileCollection set, String ... filenames) {
        assertSetContains(set, filenames as Set)
    }

    static def assertSetContainsForAllTypes(FileCollection set, String ... filenames) {
        assertSetContains(set, filenames as Set, FileCollection.AntType.values() as List)
    }

    static def assertSetContainsForAllTypes(MinimalFileTree set, String ... filenames) {
        assertSetContainsForAllTypes(new FileTreeAdapter(set), filenames)
    }

    static def assertSetContainsForAllTypes(FileCollection set, Iterable<String> filenames) {
        assertSetContains(set, filenames as Set, FileCollection.AntType.values() as List)
    }

    static def assertSetContainsForAllTypes(MinimalFileTree set, Iterable<String> filenames) {
        assertSetContainsForAllTypes(new FileTreeAdapter(set), filenames)
    }

    static def assertSetContainsForFileSet(FileCollection set, String ... filenames) {
        assertSetContains(set, filenames as Set, [FileCollection.AntType.FileSet], false)
    }

    static def assertSetContainsForFileSet(FileCollection set, Set<String> filenames) {
        assertSetContains(set, filenames, [FileCollection.AntType.FileSet], false)
    }

    static def assertSetContainsForMatchingTask(FileCollection set, String ... filenames) {
        assertSetContains(set, filenames as Set, [FileCollection.AntType.MatchingTask], false)
    }

    static def assertSetContainsForMatchingTask(FileCollection set, Set<String> filenames) {
        assertSetContains(set, filenames, [FileCollection.AntType.MatchingTask], false)
    }
}

public class FileListTask extends MatchingTask {
    final Set<String> filenames = new HashSet<String>()
    Path src

    public void addConfigured(ResourceCollection fileset) {
        Iterator<Resource> iterator = fileset.iterator()
        while (iterator.hasNext()) {
            Resource resource = iterator.next()
            assertTrue("File $resource.name found multiple times", filenames.add(resource.name.replace(File.separator, '/')))
        }
    }

    public void addConfiguredFileset(FileSet fileset) {
        addConfigured(fileset)
    }

    public Path createMatchingtask() {
        if (src == null) {
            src = new Path(getProject());
        }
        return src.createPath();
    }

    def void execute() {
        if (src) {
            src.list().each {String dirName ->
                File dir = getProject().resolveFile(dirName);
                getDirectoryScanner(dir).includedFiles.each {String fileName ->
                    assertTrue("File $fileName found multiple times", filenames.add(fileName.replace(File.separator, '/')))
                }
            }
        }
    }
}
