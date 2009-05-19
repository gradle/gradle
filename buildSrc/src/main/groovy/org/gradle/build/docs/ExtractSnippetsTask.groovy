package org.gradle.build.docs

import org.gradle.api.Project
import org.apache.tools.ant.types.FileSet
import java.util.regex.Pattern
import java.util.regex.Matcher
import org.gradle.api.GradleException
import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.DefaultTask

public class ExtractSnippetsTask extends DefaultTask {
    FileSet sourceFiles
    File destDir
    File snippetsDir

    def ExtractSnippetsTask(Project project, String name) {
        super(project, name);
        doFirst(this.&extract)
    }

    def extract() {
        ['sourceFiles', 'destDir', 'snippetsDir'].each {
            if (getProperty(it) == null) {
                throw new GradleException("Property not set: $it")
            }
        }

        DirectoryScanner scanner = sourceFiles.directoryScanner
        scanner.includedDirectories.each {String name ->
            File destDir = new File(destDir, name)
            destDir.mkdirs()
            destDir = new File(snippetsDir, name)
            destDir.mkdirs()
        }
        scanner.includedFiles.each {String name ->
            File srcFile = new File(sourceFiles.dir, name)
            File destFile = new File(destDir, name)

            destFile.parentFile.mkdirs()

            if (['.jar', '.zip'].find{ name.endsWith(it) }) {
                destFile.withOutputStream { it.write(srcFile.readBytes()) }
                return
            }

            Map writers = [
                    0: new SnippetWriter(name, destFile).start(),
                    1: new SnippetWriter(name, new File(snippetsDir, name)).start()
            ]
            Pattern startSnippetPattern = Pattern.compile('\\s*//\\s*START\\s+SNIPPET\\s+(\\S+)\\s*')
            Pattern endSnippetPattern = Pattern.compile('\\s*//\\s*END\\s+SNIPPET\\s+(\\S+)\\s*')

            try {
                // Can't use eachLine {} because it throws away blank lines
                srcFile.withReader {Reader r ->
                    BufferedReader reader = new BufferedReader(r)
                    reader.readLines().each {String line ->
                        Matcher m = startSnippetPattern.matcher(line)
                        if (m.matches()) {
                            String snippetName = m.group(1)
                            if (!writers[snippetName]) {
                                File snippetFile = new File(snippetsDir, "$name-$snippetName")
                                writers.put(snippetName, new SnippetWriter("Snippet $snippetName in $name", snippetFile))
                            }
                            writers[snippetName].start()
                            return
                        }
                        m = endSnippetPattern.matcher(line)
                        if (m.matches()) {
                            String snippetName = m.group(1)
                            writers[snippetName].end()
                            return
                        }
                        writers.values().each {SnippetWriter w ->
                            w.println(line)
                        }
                    }
                }
            } finally {
                writers.values().each {SnippetWriter w ->
                    w.close()
                }
            }
        }
    }
}

private class SnippetWriter {

    private final File dest
    private final String displayName
    private boolean appendToFile
    private PrintWriter writer

    def SnippetWriter(String displayName, File dest) {
        this.dest = dest
        this.displayName = displayName
    }

    def start() {
        if (writer) {
            throw new RuntimeException("$displayName is already started.")
        }
        dest.parentFile.mkdirs()
        writer = new PrintWriter(dest.newWriter(appendToFile), false)
        appendToFile = true
        this
    }

    def println(String line) {
        if (writer) {
            writer.println(line)
        }
    }

    def end() {
        if (!writer) {
            throw new RuntimeException("$displayName was not started.")
        }
        close()
    }

    def close() {
        if (writer) {
            writer.close()
        }
        writer = null
    }
}