/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.java
import org.gradle.api.file.SourceDirectorySet
import org.gradle.jvm.Classpath
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultJavaSourceSetTest extends Specification {
    def "has useful String representation"() {
        def sourceSet = new DefaultJavaSourceSet(new DefaultComponentSpecIdentifier("project", "javaX"), Stub(SourceDirectorySet), Stub(Classpath))

        expect:
        sourceSet.displayName == "Java source 'javaX'"
        sourceSet.toString() == "Java source 'javaX'"
    }

}
