package org.gradle.integtests

import org.apache.tools.ant.taskdefs.Expand
import org.gradle.util.AntUtil
import org.apache.tools.ant.taskdefs.Untar
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import org.apache.commons.lang.StringUtils

class TestFileHelper {
    TestFile file

    public TestFileHelper(TestFile file) {
        this.file = file
    }

    public void unzipTo(File target, boolean nativeTools) {
        // Check that each directory in hierarchy is present
        file.withInputStream {InputStream instr ->
            Set<String> dirs = [] as Set
            ZipInputStream zipStr = new ZipInputStream(instr)
            ZipEntry entry
            while (entry = zipStr.getNextEntry()) {
                if (entry.isDirectory()) {
                    assertTrue("Duplicate directory '$entry.name'", dirs.add(entry.name))
                }
                if (!entry.name.contains('/')) {
                    continue
                }
                String parent = StringUtils.substringBeforeLast(entry.name, '/') + '/'
                assertTrue("Missing dir '$parent'", dirs.contains(parent))
            }
        }

        if (nativeTools && !System.getProperty("os.name").toLowerCase().contains("windows")) {
            Process process = ['unzip', file.absolutePath, '-d', target.absolutePath].execute()
            process.consumeProcessOutput()
            assertThat(process.waitFor(), equalTo(0))
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
            Process process = builder.start()
            process.consumeProcessOutput()
            assertThat(process.waitFor(), equalTo(0))
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
        return ['ls', file.directory ? '-ld' : '-l', file.absolutePath].execute().text.split()[0].substring(0, 10)
    }
}