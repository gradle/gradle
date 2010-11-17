/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl

import org.w3c.dom.Document
import org.gradle.build.docs.XIncludeAwareXmlProvider

class DslModel {
    private final File classDocbookDir
    private final Document document
    private final Iterable<File> classpath
    private final Map<String, ClassDoc> classes = [:]
    private final Map<String, ClassMetaData> classMetaData
    private final Map<String, ExtensionMetaData> extensionMetaData

    DslModel(File classDocbookDir, Document document, Iterable<File> classpath, Map<String, ClassMetaData> classMetaData, Map<String, ExtensionMetaData> extensionMetaData) {
        this.classDocbookDir = classDocbookDir
        this.document = document
        this.classpath = classpath
        this.classMetaData = classMetaData
        this.extensionMetaData = extensionMetaData
    }

    def getClassDoc(String className) {
        ClassDoc classDoc = classes[className]
        if (classDoc == null) {
            ClassMetaData classMetaData = classMetaData[className]
            if (!classMetaData) {
                classMetaData = new ClassMetaData(null, false, "")
            }
            ExtensionMetaData extensionMetaData = extensionMetaData[className]
            if (!extensionMetaData) {
                extensionMetaData = new ExtensionMetaData(className)
            }
            File classFile = new File(classDocbookDir, "${className}.xml")
            if (!classFile.isFile()) {
                throw new RuntimeException("Docbook source file not found for class '$className' in $classDocbookDir.")
            }
            XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider(classpath)
            classDoc = new ClassDoc(className, provider.parse(classFile), classMetaData, extensionMetaData, this)
            classes[className] = classDoc
        }
        return classDoc
    }
}
