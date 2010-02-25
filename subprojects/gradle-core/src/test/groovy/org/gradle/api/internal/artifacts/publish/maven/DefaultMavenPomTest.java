/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.MavenPomListener;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.PomDependenciesConverter;
import org.gradle.util.HelperUtil;
import org.gradle.util.TestClosure;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultMavenPomTest {
    private static final String EXPECTED_PACKAGING = "something";
    private static final String EXPECTED_GROUP_ID = "someGroup";
    private static final String EXPECTED_ARTIFACT_ID = "artifactId";
    private static final String EXPECTED_VERSION = "version";

    Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private DefaultMavenPom mavenPom;
    private Conf2ScopeMappingContainer conf2ScopeMappingContainerMock;
    private PomDependenciesConverter pomDependenciesConverterStub = context.mock(PomDependenciesConverter.class);

    @Before
    public void setUp() {
        conf2ScopeMappingContainerMock = new DefaultConf2ScopeMappingContainer();
        mavenPom = new DefaultMavenPom(conf2ScopeMappingContainerMock, pomDependenciesConverterStub, new MavenProject());
        mavenPom.setPackaging(EXPECTED_PACKAGING);
        mavenPom.setGroupId(EXPECTED_GROUP_ID);
        mavenPom.setArtifactId(EXPECTED_ARTIFACT_ID);
        mavenPom.setVersion(EXPECTED_VERSION);
    }

    @Test
    public void initAndSetter() {
        assertSame(conf2ScopeMappingContainerMock, mavenPom.getScopeMappings());
        assertThat(mavenPom.getMavenProject().getModelVersion(), equalTo("4.0.0"));
        assertEquals(EXPECTED_PACKAGING, mavenPom.getPackaging());
        assertEquals(EXPECTED_ARTIFACT_ID, mavenPom.getArtifactId());
        assertEquals(EXPECTED_GROUP_ID, mavenPom.getGroupId());
        assertEquals(EXPECTED_PACKAGING, mavenPom.getPackaging());
        assertEquals(EXPECTED_VERSION, mavenPom.getVersion());
    }

    @Test
    public void addDependencies() {
        final Set<Configuration> configurations = WrapUtil.toSet(context.mock(Configuration.class));
        final List<Dependency> mavenDependencies = WrapUtil.toList(context.mock(Dependency.class));
        context.checking(new Expectations() {{
            allowing(pomDependenciesConverterStub).convert(conf2ScopeMappingContainerMock, configurations);
            will(returnValue(mavenDependencies));
        }});
        mavenPom.addDependencies(configurations);
        assertEquals(mavenDependencies, mavenPom.getMavenProject().getDependencies());
    }

    private DefaultMavenPom createTestPom(DefaultConf2ScopeMappingContainer expectedScopeMappings) {
        DefaultMavenPom sourcePom = new DefaultMavenPom(expectedScopeMappings, pomDependenciesConverterStub, new MavenProject());
        sourcePom.setArtifactId("aid");
        sourcePom.setGroupId("gid");
        sourcePom.setVersion("vrs");
        sourcePom.setPackaging("pkg");
        return sourcePom;
    }

    @Test
    public void write() throws IOException {
        final MavenProject mavenProjectMock = context.mock(MavenProject.class);
        context.checking(new Expectations() {{
            allowing(mavenProjectMock).setModelVersion(with(any(String.class)));
        }});
        DefaultMavenPom mavenPom = new DefaultMavenPom(conf2ScopeMappingContainerMock, pomDependenciesConverterStub, mavenProjectMock);
        final Writer someWriter = context.mock(Writer.class);
        context.checking(new Expectations() {{
            one(mavenProjectMock).writeModel(someWriter);
        }});
        mavenPom.write(someWriter);
    }

    @Test
    public void notifiesListener() throws IOException {
        mavenPom.addMavenPomListener(new MavenPomListener() {
            public void whenConfigured(MavenPom mavenPom) {
                mavenPom.setInceptionYear("1999");
            }
        });
        mavenPom.write(new StringWriter());
        assertThat(mavenPom.getInceptionYear(), equalTo("1999"));
    }

    @Test
    public void whenConfigured() {
        final TestClosure runnable = context.mock(TestClosure.class);
        context.checking(new Expectations() {{
            one(runnable).call(mavenPom);
        }});
        mavenPom.whenConfigured(HelperUtil.toClosure(runnable));
        mavenPom.write(new StringWriter());
    }
}
