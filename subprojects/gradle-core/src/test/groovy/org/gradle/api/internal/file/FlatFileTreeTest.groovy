package org.gradle.api.internal.file

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.api.tasks.AntBuilderAwareUtil.*
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.HelperUtil
import org.junit.Before

class FlatFileTreeTest {
    private final File testDir = HelperUtil.makeNewTestDir()
    private final File file1 = new File(testDir, "f1")
    private final File file2 = new File(testDir, "f2")

    @Before public void setUp() {
        [file1, file2].each { it.text = 'some text' }
    }

    @Test public void canGetFiles() {

        FlatFileTree tree = new FlatFileTree(file1, file2)
        assertThat(tree.files, equalTo([file1, file2] as Set))
        assertSetContains(tree, 'f1', 'f2')
    }
    
    @Test public void skipsFilesWhichDoNotExist() {
        File file1 = new File("f1")
        File file2 = new File("f2")

        FlatFileTree tree = new FlatFileTree(file1, file2)
        assertThat(tree.files, equalTo([] as Set))
        assertSetContains(tree)
    }

    @Test public void canFilterUsingClosure() {
        FlatFileTree tree = new FlatFileTree(file1, file2)
        FileTree filtered = tree.matching { exclude '*2*'}
        assertThat(filtered.files, equalTo([file1] as Set))
        assertSetContains(filtered, 'f1')
    }

    @Test public void canFilterUsingPatternSet() {
        FlatFileTree tree = new FlatFileTree(file1, file2)
        FileTree filtered = tree.matching(new PatternSet(excludes: ['*2*']))
        assertThat(filtered.files, equalTo([file1] as Set))
        assertSetContains(filtered, 'f1')
    }

    @Test public void canVisitFiles() {
        FlatFileTree tree = new FlatFileTree(file1, file2)

        Map result = [:]
        tree.visit {details -> result[details.relativePath.pathString] = details.file}

        assertThat(result, equalTo([f1: file1, f2: file2]))
    }
}
