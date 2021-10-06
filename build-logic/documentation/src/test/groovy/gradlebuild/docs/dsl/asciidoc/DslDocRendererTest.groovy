/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.docs.dsl.asciidoc

import com.github.javaparser.JavaParser
import gradlebuild.docs.dsl.source.SourceMetaDataVisitor
import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.model.SimpleClassMetaDataRepository
import spock.lang.Specification

class DslDocRendererTest extends Specification {
    def render = new DslDocRenderer()

    def "formats extracted DSL metadata"() {
        SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<ClassMetaData>()
        new JavaParser().parse("""
package foo;

/**
 MyClass is a really good class
 */
public class MyClass {
    /**
     * Returns a random number.
     */
    public int getMyInt() { return 42; }
}""").getResult().get().accept(new SourceMetaDataVisitor(), repository)
        def classMetaData = repository.get("foo.MyClass")
        def writer = new StringWriter()

        when:
        render.mergeContent(classMetaData, writer)
        println(writer.toString())
        then:
        writer.toString().contains("MyClass")
    }
}
