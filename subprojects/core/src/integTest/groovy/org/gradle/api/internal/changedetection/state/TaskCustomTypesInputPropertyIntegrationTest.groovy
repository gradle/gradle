/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskCustomTypesInputPropertyIntegrationTest extends AbstractIntegrationSpec {
    String customSerializableType() {
        """
public class CustomType implements java.io.Serializable { 
    public String value;
    
    public CustomType(String value) { this.value = value; }
    
    public boolean equals(Object o) {
        CustomType other = (CustomType)o;
        return other.value.equals(value);
    }
    
    public int hashCode() { return value.hashCode(); }
}
"""
    }

    def "task can take an input with custom type and task action defined in the build script"() {
        buildFile << """
task someTask {
    inputs.property "someValue", new CustomType("value1")
    def f = file("build/e1")
    outputs.dir f
    doLast {
        f.mkdirs()
    }
}

${customSerializableType()}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the build script
        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the value of the property
        when:
        editBuildFile('new CustomType("value1")', 'new CustomType("value2")')

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the type's implementation
        when:
        editBuildFile("public String value;", "public CharSequence value;")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task can take an input with enum type and task type defined in buildSrc"() {
        def typeSource = file("buildSrc/src/main/java/CustomType.java")
        typeSource << customSerializableType()
        file("buildSrc/src/main/java/SomeTask.java") << """
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import java.io.File;

public class SomeTask extends DefaultTask {
    public CustomType v;
    @Input
    public CustomType getV() { return v; }

    public File f;
    @OutputDirectory
    public File getF() { return f; }
    
    @TaskAction
    public void go() { }
}
"""
        buildFile << """
task someTask(type: SomeTask) {
    v = new CustomType("value1")
    f = file("build/e1")
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the build script, should not affect task state
        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the value of the property
        when:
        editBuildFile('new CustomType("value1")', 'new CustomType("value2")')

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the type's implementation
        when:
        typeSource.text = customSerializableType().replace("public String value;", "public CharSequence value;")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }
}
