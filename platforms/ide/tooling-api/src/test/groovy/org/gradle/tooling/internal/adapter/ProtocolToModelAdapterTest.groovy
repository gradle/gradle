/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.adapter

import org.gradle.internal.serialize.Message
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.util.Matchers
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.nio.channels.ByteChannel
import java.nio.channels.Channel

class ProtocolToModelAdapterTest extends Specification {
    final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter()

    def mapsNullToNull() {
        expect:
        adapter.adapt(TestModel.class, null) == null
    }

    def createsProxyAdapterForProtocolModel() {
        TestProtocolModel protocolModel = Mock()

        expect:
        adapter.adapt(TestModel.class, protocolModel) instanceof TestModel
    }

    def proxiesAreEqualWhenTargetProtocolObjectsAreEqual() {
        TestProtocolModel protocolModel1 = Mock()
        TestProtocolModel protocolModel2 = Mock()

        def model = adapter.adapt(TestModel.class, protocolModel1)
        def equal = adapter.adapt(TestModel.class, protocolModel1)
        def different = adapter.adapt(TestModel.class, protocolModel2)

        expect:
        Matchers.strictlyEquals(model, equal)
        model != different
    }

    def methodInvocationOnModelDelegatesToTheProtocolModelObject() {
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.name == 'name'
    }

    def createsProxyAdapterForMethodReturnValue() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project instanceof TestProject
        model.project.name == 'name'
    }

    def doesNotAdaptNullReturnValue() {
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getProject() >> null

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project == null
    }

    def adaptsIterableToDomainObjectSet() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getChildren() >> [protocolProject]
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.children.size() == 1
        model.children[0] instanceof TestProject
        model.children[0].name == 'name'
    }

    def adaptsIterableToCollectionType() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getChildList() >> [protocolProject]
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.childList.size() == 1
        model.childList[0] instanceof TestProject
        model.childList[0].name == 'name'
    }

    def adaptsMapElements() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.project >> protocolProject
        _ * protocolModel.getChildMap() >> Collections.singletonMap(protocolProject, protocolProject)
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.childMap.size() == 1
        model.childMap[model.project] == model.project
    }

    def adaptsEnum() {
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getTestEnum() >> TestEnum.FIRST

        expect:
        TestModel model = adapter.adapt(TestModel.class, protocolModel)
        model.testEnum == TestEnum.FIRST
    }

    def adaptsStringToEnum() {
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getTestEnum() >> "SECOND"

        expect:
        TestModel model = adapter.adapt(TestModel.class, protocolModel)
        model.testEnum == TestEnum.SECOND
    }

    def cantAdaptInvalidEnumLiteral() {
        setup:
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getTestEnum() >> "NONEXISTENT"

        when:
        TestModel model = adapter.adapt(TestModel.class, protocolModel)
        model.getTestEnum()

        then:
        thrown(IllegalArgumentException)
    }

    def cachesPropertyValues() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolModel.getChildren() >> [protocolProject]
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project.is(model.project)
        model.children.is(model.children)
    }

    def "reuses views for each object in backing graph"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolModel.getChildren() >> [protocolProject]
        _ * protocolModel.find("name") >> protocolProject

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        def project = model.project
        model.project.is(project)
        model.children[0].is(project)
        model.find("name").is(project)
    }

    def "can have path to root view"() {
        TestProtocolProjectTree parent = Mock()
        TestProtocolProjectTree child = Mock()
        parent.child >> child
        child.parent >> parent

        expect:
        def model = adapter.adapt(TestProjectTree.class, parent)
        model.child.parent.is(model)
    }

    def "does not reuse views for different objects that are equal"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject project1 = new TestProtocolProjectWithEquality()
        TestProtocolProject project2 = new TestProtocolProjectWithEquality()
        _ * protocolModel.getProject() >> project1
        _ * protocolModel.getChildList() >> [project2, project1]
        assert project1 == project2

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        !model.childList[0].is(model.project)
        model.childList[1].is(model.project)
    }

    def "does not reuse views from different graphs"() {
        TestProtocolModel protocolModel1 = Mock()
        TestProtocolModel protocolModel2 = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel1.getProject() >> protocolProject
        _ * protocolModel2.getProject() >> protocolProject

        expect:
        def model1 = adapter.adapt(TestModel.class, protocolModel1)
        def model2 = adapter.adapt(TestModel.class, protocolModel2)
        !model1.is(model2)
        !model1.project.is(model2.project)
    }

    def "can create converter that reuses views for multiple converted objects"() {
        TestProtocolModel protocolModel1 = Mock()
        TestProtocolModel protocolModel2 = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel1.getProject() >> protocolProject
        _ * protocolModel2.getProject() >> protocolProject

        expect:
        def converter = adapter.newGraph()
        def model1 = converter.adapt(TestModel.class, protocolModel1)
        def model2 = converter.adapt(TestModel.class, protocolModel2)
        !model1.is(model2)
        model1.project.is(model2.project)

        def otherModel1 = converter.adapt(TestModel.class, protocolModel1)
        model1.is(otherModel1)
    }

    def "does not reuse views with different mix ins"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject

        expect:
        def converter = adapter.newGraph()
        def model1 = converter.builder(TestModel.class).mixInTo(TestModel, ConfigMixin).build(protocolModel)

        def model2 = converter.adapt(TestModel.class, protocolModel)
        !model1.is(model2)

        def model3 = converter.builder(TestModel.class).build(protocolModel)
        model3.is(model2)

        def model4 = converter.builder(TestModel.class).mixInTo(TestModel, ConfigMixin).build(protocolModel)
        model4.is(model1)

        def model5 = converter.builder(TestModel.class).mixInTo(TestProject, "instance").build(protocolModel)
        !model5.is(model1)
        !model5.is(model2)

        def model6 = converter.builder(TestModel.class).mixInTo(TestProject, "instance").build(protocolModel)
        model6.is(model5)
    }

    def "does not reuse views when some view reachable has different mix ins"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject

        expect:
        def converter = adapter.newGraph()
        def model1 = converter.builder(TestModel.class).mixInTo(TestProject, ProjectMixin).build(protocolModel)

        def model2 = converter.adapt(TestModel.class, protocolModel)
        !model2.is(model1)
        !model1.project.is(model2.project)

        def model3 = converter.builder(TestModel.class).build(protocolModel)
        model3.is(model2)

        def model4 = converter.builder(TestModel.class).mixInTo(TestProject, ProjectMixin).build(protocolModel)
        model4.is(model1)

        def model5 = converter.builder(TestModel.class).mixInTo(TestProject, "instance").build(protocolModel)
        !model5.is(model1)
        !model5.project.is(model1.project)
        !model5.is(model2)
        !model5.project.is(model2.project)

        def model6 = converter.builder(TestModel.class).mixInTo(TestProject, "instance").build(protocolModel)
        model6.is(model5)
    }

    def "reuses reachable views when they have the same mix ins"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolModel protocolModel2 = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolModel2.getProject() >> protocolProject

        expect:
        def converter = adapter.newGraph()
        def model1 = converter.builder(TestModel.class).mixInTo(TestModel, ConfigMixin).build(protocolModel)

        def model2 = converter.adapt(TestModel.class, protocolModel)
        !model2.is(model1)
        model2.project.is(model1.project)

        def model3 = converter.builder(TestModel.class).mixInTo(TestModel, "thing").build(protocolModel2)
        !model3.is(model1)
        model3.project.is(model1.project)
    }

    def "ignores mix ins that are not relevant for view type"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject

        expect:
        def converter = adapter.newGraph()
        def model1 = converter.builder(TestModel.class).mixInTo(Long, ConfigMixin).mixInTo(Runnable, "instance").build(protocolModel)

        def model2 = converter.builder(TestModel.class).mixInTo(Runnable, "instance").build(protocolModel)
        model2.is(model1)

        def model3 = converter.builder(TestModel.class).build(protocolModel)
        model3.is(model1)
    }

    def "backing object can be viewed as various types"() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolModel.getExtendedProject() >> protocolProject

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project.is(model.project)
        model.extendedProject
    }

    def reportsMethodWhichDoesNotExistOnProtocolObject() {
        PartialTestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project

        then:
        UnsupportedMethodException e = thrown()
        e.message.contains "TestModel.getProject()"
    }

    def propagatesExceptionThrownByProtocolObject() {
        TestProtocolModel protocolModel = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.name

        then:
        protocolModel.name >> { throw failure }
        RuntimeException e = thrown()
        e == failure
    }

    def isPropertySupportedMethodReturnsTrueWhenProtocolObjectHasAssociatedProperty() {
        TestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.configSupported
    }

    def isPropertySupportedMethodReturnsFalseWhenProtocolObjectDoesNotHaveAssociatedProperty() {
        PartialTestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        !model.configSupported
    }

    def safeGetterDelegatesToProtocolObject() {
        TestProtocolModel protocolModel = Mock()

        given:
        protocolModel.config >> "value"

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.getConfig("default") == "value"
    }

    def safeGetterDelegatesReturnsDefaultValueWhenProtocolObjectDoesNotHaveAssociatedProperty() {
        PartialTestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.getConfig("default") == "default"
    }

    def safeGetterDelegatesReturnsDefaultValueWhenPropertyValueIsNull() {
        TestProtocolModel protocolModel = Mock()

        given:
        protocolModel.config >> null

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.getConfig("default") == "default"
    }

    def "can use safe getter for boolean properties"() {
        TestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.isThing(true)
    }

    def "can mix in methods from another bean class"() {
        PartialTestProtocolModel protocolModel = Mock()

        given:
        protocolModel.name >> 'name'

        when:
        def model = adapter.builder(TestModel.class).mixInTo(TestModel, ConfigMixin).build(protocolModel)

        then:
        model.name == "name"
        model.project
        model.getConfig('default') == "[default]"
    }

    def "can mix in methods from another bean instance"() {
        PartialTestProtocolModel protocolModel = Mock()
        TestProject project = Mock()

        given:
        protocolModel.name >> 'name'

        when:
        def model = adapter.builder(TestModel.class).mixInTo(TestModel.class, new FixedMixin(project: project)).build(protocolModel)

        then:
        model.project == project
        model.getConfig('default') == "[default]"
    }

    def "getter on mix in can accept the view as a parameter"() {
        PartialTestProtocolModel protocolModel = Mock()
        TestProject project = Mock()

        given:
        protocolModel.name >> 'name'
        project.name >> 'name'

        when:
        def model = adapter.builder(TestModel.class).mixInTo(TestModel.class, new FixedMixin(project: project)).build(protocolModel)

        then:
        model.project.name == "name"
        model.projectName == "[name]"
    }

    def "adapts values returned from mix in beans"() {
        PartialTestProtocolModel protocolModel = Mock()

        given:
        protocolModel.name >> 'name'

        when:
        def model = adapter.builder(TestModel.class).mixInTo(TestModel, ConfigMixin).build(protocolModel)

        then:
        model.project != null
    }

    def "delegates to type provider to determine type to wrap an object in"() {
        def typeProvider = Mock(TargetTypeProvider)
        def adapter = new ProtocolToModelAdapter(typeProvider)
        def sourceObject = new Object()

        given:
        _ * typeProvider.getTargetType(Channel, sourceObject) >> ByteChannel

        when:
        def result = adapter.adapt(Channel.class, sourceObject)

        then:
        result instanceof ByteChannel
    }

    def "view object can be serialized"() {
        def protocolModel = new TestModelImpl()

        given:
        def model = adapter.adapt(TestModel.class, protocolModel)
        def copiedModel = serialize(model)

        expect:
        copiedModel instanceof TestModel
        copiedModel != model
        copiedModel.name == "model"
        copiedModel.project.name == "name"
    }

    def "view identity is retained when serialized"() {
        def protocolModel = new TestModelImpl()

        given:
        def model = adapter.adapt(TestModel.class, protocolModel)
        def copy = serialize([model, model.project, model.childList])

        expect:
        def copiedModel = copy[0]
        def copiedProject = copy[1]
        def projectList = copy[2]
        copiedProject.is(copiedModel.project)
        projectList[0].is(copiedModel.project)
    }

    def "view object with mix-in class can be serialized"() {
        def protocolModel = new TestModelImpl()

        given:
        def model = adapter.builder(TestModel.class).mixInTo(TestModel, ConfigMixin).build(protocolModel)
        def copiedModel = serialize(model)

        expect:
        copiedModel instanceof TestModel
        copiedModel != model
        copiedModel.name == "model"
        copiedModel.getConfig("thing") == "[thing]"
    }

    def "view object with mix-in can be serialized"() {
        def protocolModel = new TestModelImpl()

        given:
        def model = adapter.builder(TestModel.class).mixInTo(TestModel, new FixedMixin()).build(protocolModel)
        def copiedModel = serialize(model)

        expect:
        copiedModel instanceof TestModel
        copiedModel != model
        copiedModel.name == "model"
        copiedModel.getConfig("thing") == "[thing]"
    }

    def serialize(Object model) {
        def serialized = new ByteArrayOutputStream()
        Message.send(model, serialized)
        return Message.receive(new ByteArrayInputStream(serialized.toByteArray()), getClass().classLoader)
    }

    def "unpacks source object from view"() {
        def source = new Object()

        given:
        def view = adapter.adapt(TestProject.class, source)

        expect:
        adapter.unpack(view).is(source)
    }

    def "fails when source object is not a view object"() {
        when:
        adapter.unpack("not a view")

        then:
        thrown(IllegalArgumentException)

        when:
        adapter.unpack(java.lang.reflect.Proxy.newProxyInstance(getClass().classLoader, [Runnable] as Class[], Stub(InvocationHandler)))

        then:
        thrown(IllegalArgumentException)
    }
}

interface TestModel {
    String getName()

    TestProject getProject()

    String getProjectName()

    TestExtendedProject getExtendedProject()

    boolean isConfigSupported()

    String getConfig(String defaultValue)

    Boolean isThing(Boolean defaultValue)

    TestProject find(String name)

    DomainObjectSet<? extends TestProject> getChildren()

    List<TestProject> getChildList()

    Map<TestProject, TestProject> getChildMap()

    TestEnum getTestEnum()
}

interface TestProject {
    String getName()
}

interface TestExtendedProject extends TestProject {
}

interface TestProjectTree extends TestProject {
    TestProjectTree getParent()
    TestProjectTree getChild()
}

interface TestProtocolModel {
    String getName()

    TestProtocolProject getProject()

    TestProtocolProject getExtendedProject()

    TestProtocolProject find(String name)

    Iterable<? extends TestProtocolProject> getChildren()

    Iterable<? extends TestProtocolProject> getChildList()

    Map<String, ? extends TestProtocolProject> getChildMap()

    String getConfig();

    Object getTestEnum()
}

interface PartialTestProtocolModel {
    String getName()
}

interface TestProtocolProject {
    String getName()
}

interface TestProtocolProjectTree {
    String getName()
    TestProtocolProjectTree getParent()
    TestProtocolProjectTree getChild()
}

enum TestEnum {
    FIRST, SECOND
}

class TestModelImpl implements Serializable {
    String name = "model"
    TestProtocolProjectImpl project = new TestProtocolProjectImpl()
    List<TestProtocolProjectImpl> childList = [project]
}

class TestProtocolProjectImpl implements Serializable {
    String name = "name"
}

class TestProtocolProjectWithEquality implements TestProtocolProject, Serializable {
    String name = "name"

    @Override
    boolean equals(Object obj) {
        return obj.name == name
    }

    @Override
    int hashCode() {
        return name.hashCode()
    }
}

class ConfigMixin {
    TestModel model

    ConfigMixin(TestModel model) {
        this.model = model
    }

    Object getProject() {
        return new Object()
    }

    String getConfig(String value) {
        return "[${model.getConfig(value)}]"
    }

    String getName() {
        return "[${model.name}]"
    }
}

class ProjectMixin {
}

class FixedMixin implements Serializable {
    TestProject project

    String getConfig(String config) {
        return "[$config]"
    }

    String getProjectName(TestModel model) {
        return "[$model.project.name]"
    }
}
