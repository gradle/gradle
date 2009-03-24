package org.gradle.build.docs

import org.gradle.api.internal.DefaultTask
import org.gradle.api.Project
import org.apache.tools.ant.types.FileSet
import java.util.regex.Pattern
import java.util.regex.Matcher
import org.gradle.api.GradleException

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
        
        sourceFiles.directoryScanner.includedFiles.each {String name ->
            File srcFile = new File(sourceFiles.dir, name)
            File destFile = new File(destDir, name)

            destFile.parentFile.mkdirs()

            if (!name.endsWith('.gradle')) {
                destFile.write(srcFile.text)
                return
            }
            
            Map writers = ['full': new PrintWriter(new FileWriter(destFile), false)]
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
                            File snippetFile = new File(snippetsDir, "$name-$snippetName")
                            snippetFile.parentFile.mkdirs()
                            writers.put(snippetName, new PrintWriter(new FileWriter(snippetFile)))
                            return
                        }
                        m = endSnippetPattern.matcher(line)
                        if (m.matches()) {
                            String snippetName = m.group(1)
                            writers.remove(snippetName).close()
                            return
                        }
                        writers.values().each {PrintWriter w ->
                            w.println(line)
                        }
                    }
                }
            } finally {
                writers.values().each {Writer w ->
                    w.close()
                }
            }
        }
    }
}