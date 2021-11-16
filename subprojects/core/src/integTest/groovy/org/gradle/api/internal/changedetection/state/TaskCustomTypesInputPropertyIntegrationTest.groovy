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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.Actions

class TaskCustomTypesInputPropertyIntegrationTest extends AbstractIntegrationSpec {
    String customSerializableType() {
        """
import java.io.Serializable;

public class CustomType implements Serializable {
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

    String customSerializableTypeWithNonDeterministicSerializedForm() {
        // A type where equals() may return true for values where the serialized forms are different

        """
import java.io.Serializable;

public class CustomType implements Serializable {
    public String value;

    public CustomType(String value) { this.value = value; }

    public boolean equals(Object o) {
        CustomType other = (CustomType)o;
        return other.value.startsWith(value) || value.startsWith(other.value);
    }

    public int hashCode() { return 1; }
}
"""
    }

    String customTaskType() {
        """
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Optional;
import java.io.File;

public class SomeTask extends DefaultTask {
    public CustomType v;
    @Input @Optional
    public CustomType getV() { return v; }

    public File f;
    @OutputDirectory
    public File getF() { return f; }

    @TaskAction
    public void go() { }
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
        buildFile.replace('new CustomType("value1")', 'new CustomType("value2")')

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
        buildFile.replace("public String value;", "public CharSequence value;")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task can take an input with custom type defined in a build script plugin"() {
        def otherScript = file("other.gradle")
        otherScript << """
someTask.inputs.property "someValue", new CustomType("value1")

${customSerializableType()}
"""
        buildFile """
task someTask {
    def f = file("build/e1")
    outputs.dir f
    doLast {
        f.mkdirs()
    }
}

apply from: 'other.gradle'
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the value of the property
        when:
        otherScript.replace('new CustomType("value1")', 'new CustomType("value2")')

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
        otherScript.replace("public String value;", "public CharSequence value;")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "task can take an input with custom type and task type defined in buildSrc"() {
        def typeSource = file("buildSrc/src/main/java/CustomType.java")
        typeSource << customSerializableType()
        file("buildSrc/src/main/java/SomeTask.java") << customTaskType()

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
        buildFile.replace('new CustomType("value1")', 'new CustomType("value2")')

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
        typeSource.replace("public String value;", "public CharSequence value;")

        and:
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "can use null value for task input property"() {
        buildFile << customSerializableType()
        buildFile << customTaskType()

        buildFile << """
task someTask(type: SomeTask) {
    v = null
    f = file("build/out")
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    def "using a custom type with non-deterministic serialized form is deprecated"() {
        file("buildSrc/src/main/java/CustomType.java") << customSerializableTypeWithNonDeterministicSerializedForm()
        buildFile << """
task someTask {
    inputs.property "someValue", new CustomType("value1")
    outputs.dir file("build/out")
    doLast ${Actions.name}.doNothing()
}

"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change to "equal" value
        when:
        buildFile.replace('new CustomType("value1")', 'new CustomType("value1 ignore me")')
        executer.expectDocumentedDeprecationWarning("Using objects as inputs that have a different serialized form but are equal has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. " +
            "Type 'CustomType' has a custom implementation for equals(). " +
            "Declare the property as @Nested instead to expose its properties as inputs. " +
            "See https://docs.gradle.org/current/userguide/upgrading_version_7.html#equals_up_to_date_deprecation for more details.")
        run "someTask"

        then:
        skipped(":someTask")

        // Change to different value
        when:
        buildFile.replace('new CustomType("value1 ignore me")', 'new CustomType("value2")')
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Value of input property 'someValue' has changed for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")
    }

    @ToBeFixedForConfigurationCache(because = "ClassNotFoundException: ArrayList1_groovyProxy", iterationMatchers = '.*\\[type: Map, #2\\]$')
    def "task can take as input a collection of custom types from various sources"() {
        def buildSrcType = file("buildSrc/src/main/java/CustomType.java")
        buildSrcType << customSerializableType()
        def otherScript = file("other.gradle")
        otherScript << """
class ScriptPluginType extends CustomType {
    ScriptPluginType(String value) { super(value) }
}
ext.pluginValue = new ScriptPluginType("abc")
"""

        buildFile << """
class ScriptType extends CustomType {
    ScriptType(String value) { super(value) }
}

apply from: 'other.gradle'

task someTask {
    inputs.property("v", [new CustomType('123'), new ScriptType('abc'), pluginValue] as $type)
    outputs.file file("build/out")
    doLast ${Actions.name}.doNothing()
}
"""

        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the values of the property
        when:
        buildFile.replace("[new CustomType('123'), new ScriptType('abc'), pluginValue] as $type", "[new CustomType('abc'), new ScriptType('123'), pluginValue] as $type")

        and:
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Value of input property 'v' has changed for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        // Change the values of the property in script plugin
        when:
        otherScript.replace('new ScriptPluginType("abc")', 'new ScriptPluginType("1234")')

        and:
        executer.withArgument("-i")
        run "someTask"

        then:
        executedAndNotSkipped(":someTask")
        outputContains("Value of input property 'v' has changed for task ':someTask'")

        when:
        run "someTask"

        then:
        skipped(":someTask")

        where:
        type           | _
        "List"         | _
        "Set"          | _
        "Map"          | _
        "Object[]"     | _
        "CustomType[]" | _
    }

}
