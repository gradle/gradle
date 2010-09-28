package org.gradle.build.docs

import org.w3c.dom.Document

class DslModel {
    private final File classDocbookDir
    private final Document document
    private final Iterable<File> classpath
    private final Map<String, ClassDoc> classes = [:]

    DslModel(File classDocbookDir, Document document, Iterable<File> classpath) {
        this.classDocbookDir = classDocbookDir
        this.document = document
        this.classpath = classpath
    }

    def getClassDoc(String className) {
        ClassDoc classDoc = classes[className]
        if (classDoc == null) {
            File classFile = new File(classDocbookDir, "${className}.xml")
            XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider(classpath)
            classDoc = new ClassDoc(className, provider.parse(classFile), this)
            classes[className] = classDoc
        }
        return classDoc
    }
}
