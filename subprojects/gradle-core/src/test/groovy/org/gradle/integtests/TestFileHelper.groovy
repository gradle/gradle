package org.gradle.integtests

import org.apache.tools.ant.taskdefs.Expand
import org.gradle.util.AntUtil
import org.apache.tools.ant.taskdefs.Untar
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class TestFileHelper {
    TestFile file

    public TestFileHelper(TestFile file) {
        this.file = file
    }

    public void unzipTo(File target, boolean nativeTools) {
        if (nativeTools && !System.getProperty("os.name").toLowerCase().contains("windows")) {
            assertThat(['unzip', file.absolutePath, '-d', target.absolutePath].execute().waitFor(), equalTo(0))
            return
        }

        Expand unzip = new Expand();
        unzip.src = file;
        unzip.dest = target;
        AntUtil.execute(unzip);
    }

    public void untarTo(File target, boolean nativeTools) {
        if (nativeTools && !System.getProperty("os.name").toLowerCase().contains("windows")) {
            target.mkdirs()
            ProcessBuilder builder = new ProcessBuilder(['tar', '-xf', file.absolutePath])
            builder.directory(target)
            assertThat(builder.start().waitFor(), equalTo(0))
            return
        }

        Untar untar = new Untar();
        untar.setSrc(file);
        untar.setDest(target);

        if (file.getName().endsWith(".tgz")) {
            Untar.UntarCompressionMethod method = new Untar.UntarCompressionMethod();
            method.setValue("gzip");
            untar.setCompression(method);
        } else if (file.getName().endsWith(".tbz2")) {
            Untar.UntarCompressionMethod method = new Untar.UntarCompressionMethod();
            method.setValue("bzip2");
            untar.setCompression(method);
        }

        AntUtil.execute(untar);
    }

    public String getPermissions() {
        return ['ls', file.directory ? '-ld' : '-l', file.absolutePath].execute().text.split()[0]
    }
}