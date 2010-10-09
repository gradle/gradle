package org.gradle.build.docs

import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import groovy.xml.dom.DOMUtil

class XIncludeAwareXmlProvider {
    final Iterable<java.io.File> classpath
    Element root

    XIncludeAwareXmlProvider(Iterable<File> classpath) {
        this.classpath = classpath
    }

    Element parse(File sourceFile) {
        System.setProperty("org.apache.xerces.xni.parser.XMLParserConfiguration",
                "org.apache.xerces.parsers.XIncludeParserConfiguration")

        // Set the thread context classloader to pick up the correct XML parser
        def uris = classpath.collect {it.toURI().toURL()}
        def classloader = new URLClassLoader(uris as URL[], getClass().classLoader)
        def oldClassloader = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(classloader)
        try {
            root = parseSourceFile(sourceFile).documentElement
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassloader)
        }
        return root
    }

    void write(File destFile) {
        destFile.withOutputStream {OutputStream stream ->
            DOMUtil.serialize(root, stream)
        }
    }

    Document getDocument() {
        return root.ownerDocument
    }
    
    private Document parseSourceFile(File sourceFile) {
        DocumentBuilderFactory factory = Class.forName('com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl', true, Thread.currentThread().contextClassLoader).newInstance()
        factory.setNamespaceAware(true)
        DocumentBuilder builder = factory.newDocumentBuilder()
        return builder.parse(sourceFile)
    }

}
