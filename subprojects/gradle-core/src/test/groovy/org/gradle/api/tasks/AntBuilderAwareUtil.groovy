package org.gradle.api.tasks

import org.apache.tools.ant.Task
import org.apache.tools.ant.types.Resource
import org.apache.tools.ant.types.ResourceCollection
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class AntBuilderAwareUtil {
    static def assertSetContains(AntBuilderAware set, Set<String> filenames) {
        AntBuilder ant = new AntBuilder()
        ant.antProject.addTaskDefinition('test', FileListTask)
        FileListTask task = ant.test {
            set.addToAntBuilder(ant, null)
        }

        assertThat(task.filenames, equalTo(filenames))
    }

    static def assertSetContains(AntBuilderAware set, String ... filenames) {
        assertSetContains(set, filenames as Set)
    }
}

public static class FileListTask extends Task {
    final Set<String> filenames = new HashSet<String>()

    public void addConfigured(ResourceCollection fileset) {
        Iterator<Resource> iterator = fileset.iterator()
        while (iterator.hasNext()) {
            Resource resource = iterator.next()
            filenames.add(resource.getName().replace(File.separator, '/'))
        }
    }
}
