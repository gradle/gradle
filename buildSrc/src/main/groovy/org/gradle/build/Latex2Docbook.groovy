package org.gradle.build

import org.gradle.api.Project
import java.util.regex.Pattern
import java.util.regex.Matcher

public class Latex2Docbook {
    private Project project
    File sourceDir
    File destDir

    def Latex2Docbook(Project project) {
        this.project = project
    }

    def transform() {
        if (destDir == null) {
            throw new RuntimeException('Destination directory not specified')
        }
        if (sourceDir == null) {
            throw new RuntimeException('Source directory not specified')
        }

        sourceDir.eachFileRecurse {File src ->
            if (src.name.endsWith('.tex')) {
                transform(src)
            }
        }
    }

    private def transform(File src) {
        File dest = new File(destDir, src.name.replaceAll('\\.tex$', '.xml'))
        println "Transform $src.name -> $dest.name"
        dest.parentFile.mkdirs()
        dest.withPrintWriter {PrintWriter writer ->
            String text = src.text
            RootTagHandler handler = new RootTagHandler(writer)
            new LatexParser().parse(text, handler)
            handler.end()
        }
    }

}