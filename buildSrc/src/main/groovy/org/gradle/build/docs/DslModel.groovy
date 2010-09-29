package org.gradle.build.docs

import org.w3c.dom.Document

class DslModel {
    private final File classDocbookDir
    private final Document document
    private final Iterable<File> classpath
    private final Map<String, ClassDoc> classes = [:]
    private final Map<String, ClassMetaData> classMetaData

    DslModel(File classDocbookDir, Document document, Iterable<File> classpath, Map<String, ClassMetaData> classMetaData) {
        this.classDocbookDir = classDocbookDir
        this.document = document
        this.classpath = classpath
        this.classMetaData = classMetaData
    }

    def getClassDoc(String className) {
        ClassDoc classDoc = classes[className]
        if (classDoc == null) {
            ClassMetaData classMetaData = classMetaData[className]
            if (!classMetaData) {
                classMetaData = new ClassMetaData(null, false)
            }
            File classFile = new File(classDocbookDir, "${className}.xml")
            if (!classFile.isFile()) {
                throw new RuntimeException("Docbook source file not found for class '$className' in $classDocbookDir.")
            }
            XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider(classpath)
            classDoc = new ClassDoc(className, provider.parse(classFile), classMetaData, this)
            classes[className] = classDoc
        }
        return classDoc
    }
}
