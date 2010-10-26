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
package org.gradle.plugins.eclipse.model

import org.gradle.api.internal.XmlTransformer

import org.gradle.api.Action

/**
 * Represents the customizable elements of an eclipse classpath file. (via XML hooks everything is customizable).
 *
 * @author Hans Dockter
 */
class Classpath {
    /**
     * The classpath entries (contains by default an output entry pointing to bin).
     */
    List entries = [new Output('bin')]

    private Node xml

    private XmlTransformer xmlTransformer

    Classpath(Action<Classpath> beforeConfiguredAction, Action<Classpath> whenConfiguredAction, XmlTransformer xmlTransformer, List entries, Reader inputXml) {
        initFromXml(inputXml)

        beforeConfiguredAction.execute(this)

        this.entries.addAll(entries)
        this.entries.unique()
        this.xmlTransformer = xmlTransformer

        whenConfiguredAction.execute(this)
    }

    private def initFromXml(Reader inputXml) {
        if (!inputXml) {
            xml = new Node(null, 'classpath')
            return
        }

        xml = new XmlParser().parse(inputXml)

        xml.classpathentry.each { entryNode ->
            def entry = null
            switch (entryNode.@kind) {
                case 'src':
                    def path = entryNode.@path
                    entry = path.startsWith('/') ? new ProjectDependency(entryNode) : new SourceFolder(entryNode)
                    break
                case 'var': entry = new Variable(entryNode)
                    break
                case 'con': entry = new Container(entryNode)
                    break
                case 'lib': entry = new Library(entryNode)
                    break
                case 'output': entry = new Output(entryNode)
                    break
            }
            if (entry) {
                entries.add(entry)
            }
        }
    }

    void toXml(File file) {
        file.withWriter { Writer writer -> toXml(writer) }
    }

    def toXml(Writer writer) {
        xml.classpathentry.each { xml.remove(it) }
        entries.each { ClasspathEntry entry ->
            entry.appendNode(xml)
        }
        xmlTransformer.transform(xml, writer)
    }
    
    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Classpath classpath = (Classpath) o;

        if (entries != classpath.entries) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = entries.hashCode();
        return result;
    }

    public String toString() {
        return "Classpath{" +
                "entries=" + entries +
                '}';
    }
}
