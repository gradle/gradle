/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.TestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification

abstract class AbstractResourceBasedTestingCrossVersionTest extends ToolingApiSpecification implements TestEventsFixture {

    protected static final DEFAULT_DEFINITIONS_LOCATION = "src/test/definitions"
    private static File engineJarLib

    ProgressEvents events = ProgressEvents.create()

    def setup() {
        String engineCopyToDirName = "test-engine-build"
        writeCustomTestSourceEmittingTestEngineSources(engineCopyToDirName)
        File engineBuildDir = file(engineCopyToDirName)
        withConnection(connector().forProjectDirectory(engineBuildDir)) {
            it.newBuild().forTasks("build").run()
        }
        engineJarLib = engineBuildDir.file("build/libs/${engineCopyToDirName}.jar")
    }

    protected String enableEngineForSuite() {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${TextUtil.normaliseFileSeparators(engineJarLib.absolutePath)}")
                }
        """
    }

    protected void writeTestDefinitions(String path = "src/test/definitions") {
        file("$path/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
                <test name="bar" />
            </tests>
        """
        file("$path/subSomeOtherTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="other" />
            </tests>
        """
    }

    private void writeCustomTestSourceEmittingTestEngineSources(String basePath) {
        file("$basePath/build.gradle.kts") << """
            plugins {
                `java-library`
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                api("org.junit.jupiter:junit-jupiter-engine:5.13.4")
            }
        """
        file("$basePath/settings.gradle.kts") << 'rootProject.name = "test-engine-build"'
        file("$basePath/src/main/java/org/gradle/testing/testengine/descriptor/ResourceBasedTestDescriptor.java") << """
            package org.gradle.testing.testengine.descriptor;

            import org.junit.platform.engine.TestSource;
            import org.junit.platform.engine.UniqueId;
            import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
            import org.junit.platform.engine.support.descriptor.FileSource;

            import java.io.File;
            import java.util.Optional;

            public final class ResourceBasedTestDescriptor extends AbstractTestDescriptor {
                private final File file;
                private final String name;
                private final boolean dynamic;

                public ResourceBasedTestDescriptor(UniqueId parentId, File file, String name) {
                    this(parentId, file, name, false);
                }

                public ResourceBasedTestDescriptor(UniqueId parentId, File file, String name, boolean dynamic) {
                    super(parentId.append("testDefinitionFile", file.getName()).append("testDefinition", name), file.getName() + " - " + name);
                    this.file = file;
                    this.name = name;
                    this.dynamic = dynamic;
                }

                @Override
                public Type getType() {
                    return dynamic ? Type.CONTAINER_AND_TEST : Type.TEST;
                }

                @Override
                public boolean mayRegisterTests() {
                    return dynamic;
                }

                @Override
                public Optional<TestSource> getSource() {
                    return Optional.of(FileSource.from(getFile()));
                }

                public File getFile() {
                    return file;
                }

                public String getName() {
                    return name;
                }

                @Override
                public String toString() {
                    return "Test[file=" + file.getName() + ", name=" + name + "]";
                }
            }
        """
        file("$basePath/src/main/java/org/gradle/testing/testengine/engine/ResourceBasedSelectorResolver.java") << """
            package org.gradle.testing.testengine.engine;

            import org.gradle.testing.testengine.descriptor.ResourceBasedTestDescriptor;
            import org.gradle.testing.testengine.util.DirectoryScanner;
            import org.junit.platform.commons.logging.Logger;
            import org.junit.platform.commons.logging.LoggerFactory;
            import org.junit.platform.engine.DiscoverySelector;
            import org.junit.platform.engine.discovery.DirectorySelector;
            import org.junit.platform.engine.discovery.DiscoverySelectors;
            import org.junit.platform.engine.discovery.FileSelector;
            import org.junit.platform.engine.support.discovery.SelectorResolver;

            import java.io.File;
            import java.util.ArrayList;
            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Optional;
            import java.util.Set;
            import java.util.stream.Collectors;

            public class ResourceBasedSelectorResolver implements SelectorResolver {
                public static final Logger LOGGER = LoggerFactory.getLogger(ResourceBasedSelectorResolver.class);

                private final DirectoryScanner directoryScanner = new DirectoryScanner();
                private final boolean dynamic;

                public ResourceBasedSelectorResolver() {
                    this(false);
                }

                public ResourceBasedSelectorResolver(boolean dynamic) {
                    this.dynamic = dynamic;
                }

                @Override
                public Resolution resolve(DirectorySelector selector, Context context) {
                    List<File> contents = new ArrayList<>(directoryScanner.scanDirectory(selector.getDirectory(), true));

                    if (!contents.isEmpty()) {
                        Set<DiscoverySelector> selectors = new LinkedHashSet<>();
                        for (File file : contents) {
                            if (file.isFile()) {
                                selectors.add(DiscoverySelectors.selectFile(file.getAbsolutePath()));
                            } else if (file.isDirectory()) {
                                selectors.add(DiscoverySelectors.selectDirectory(file.getAbsolutePath()));
                            }
                        }

                        return Resolution.selectors(selectors);
                    } else {
                        return Resolution.unresolved();
                    }
                }

                @Override
                public Resolution resolve(FileSelector selector, Context context) {
                    File file = selector.getFile();
                    if (directoryScanner.getTestFileParser().isValidTestDefinitionFile(file)) {
                        LOGGER.info(() -> "Test specification file: " + file.getAbsolutePath());

                        Set<Match> tests = directoryScanner.getTestFileParser().parseTestNames(file).stream()
                                .map(testName -> context.addToParent(parent -> Optional.of(new ResourceBasedTestDescriptor(parent.getUniqueId(), file, testName, dynamic))))
                                .map(Optional::get)
                                .map(Match::exact)
                                .collect(Collectors.toSet());

                        if (!tests.isEmpty()) {
                            return Resolution.matches(tests);
                        }
                    }

                    return Resolution.unresolved();
                }
            }
        """
        file("$basePath/src/main/java/org/gradle/testing/testengine/util/DirectoryScanner.java") << """
            package org.gradle.testing.testengine.util;

            import java.io.File;
            import java.util.ArrayList;
            import java.util.List;

            public final class DirectoryScanner {
                private TestFileParser testFileParser = new TestFileParser();

                public TestFileParser getTestFileParser() {
                    return testFileParser;
                }

                public List<File> scanDirectory(File dir) {
                    return scanDirectory(dir, false);
                }

                public List<File> scanDirectory(File dir, boolean includeDirs) {
                    List<File> filesFound = new ArrayList<>();
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isDirectory()) {
                                if (includeDirs) {
                                    filesFound.add(file);
                                }
                                filesFound.addAll(scanDirectory(file));
                            } else if (testFileParser.isValidTestDefinitionFile(file)) {
                                filesFound.add(file);
                            }
                        }
                    }
                    return filesFound;
                }
            }
        """
        file("$basePath/src/main/java/org/gradle/testing/testengine/util/TestFileParser.java") << """
            package org.gradle.testing.testengine.util;

            import org.w3c.dom.Document;
            import org.w3c.dom.Element;
            import org.w3c.dom.NodeList;

            import javax.xml.parsers.DocumentBuilder;
            import javax.xml.parsers.DocumentBuilderFactory;
            import java.io.File;
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;

            public final class TestFileParser {
                public boolean isValidTestDefinitionFile(File file) {
                    return file.exists() && file.isFile() && file.canRead()
                            && (file.getName().toLowerCase().endsWith(".xml") || file.getName().toLowerCase().endsWith(".rbt"));
                }

                public List<String> parseTestNames(File testDefinitionsFile) {
                    List<String> names = new ArrayList<>();

                    try {
                        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                        Document doc = dBuilder.parse(testDefinitionsFile);
                        doc.getDocumentElement().normalize();

                        NodeList nodeList = doc.getElementsByTagName("test");

                        for (int i = 0; i < nodeList.getLength(); i++) {
                            Element testElement = (Element) nodeList.item(i);
                            String name = testElement.getAttribute("name");
                            names.add(name);
                        }
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }

                    return names;
                }
            }
        """
        file("$basePath/src/main/resources/META-INF/services/org.junit.platform.engine.TestEngine") << 'org.gradle.testing.testengine.engine.CustomTestSourceDeclaringTestEngine'
        file("$basePath/src/main/java/org/gradle/testing/testengine/engine/CustomResourceBasedSelectorResolver.java") << """
            package org.gradle.testing.testengine.engine;

            import org.gradle.testing.testengine.descriptor.CustomSourceTestDescriptor;
            import org.gradle.testing.testengine.util.DirectoryScanner;
            import org.junit.platform.commons.logging.Logger;
            import org.junit.platform.commons.logging.LoggerFactory;
            import org.junit.platform.engine.DiscoverySelector;
            import org.junit.platform.engine.discovery.DirectorySelector;
            import org.junit.platform.engine.discovery.DiscoverySelectors;
            import org.junit.platform.engine.discovery.FileSelector;
            import org.junit.platform.engine.support.discovery.SelectorResolver;

            import java.io.File;
            import java.util.ArrayList;
            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Optional;
            import java.util.Set;
            import java.util.stream.Collectors;

            public class CustomResourceBasedSelectorResolver implements SelectorResolver {
                public static final Logger LOGGER = LoggerFactory.getLogger(ResourceBasedSelectorResolver.class);

                private final DirectoryScanner directoryScanner = new DirectoryScanner();

                @Override
                public Resolution resolve(DirectorySelector selector, Context context) {
                    List<File> contents = new ArrayList<>(directoryScanner.scanDirectory(selector.getDirectory(), true));

                    if (!contents.isEmpty()) {
                        Set<DiscoverySelector> selectors = new LinkedHashSet<>();
                        for (File file : contents) {
                            if (file.isFile()) {
                                selectors.add(DiscoverySelectors.selectFile(file.getAbsolutePath()));
                            } else if (file.isDirectory()) {
                                selectors.add(DiscoverySelectors.selectDirectory(file.getAbsolutePath()));
                            }
                        }

                        return Resolution.selectors(selectors);
                    } else {
                        return Resolution.unresolved();
                    }
                }

                @Override
                public Resolution resolve(FileSelector selector, Context context) {
                    File file = selector.getFile();
                    if (directoryScanner.getTestFileParser().isValidTestDefinitionFile(file)) {
                        LOGGER.info(() -> "Test specification file: " + file.getAbsolutePath());

                        Set<Match> tests = directoryScanner.getTestFileParser().parseTestNames(file).stream()
                                .map(testName -> context.addToParent(parent -> Optional.of(new CustomSourceTestDescriptor(parent.getUniqueId(), file, testName))))
                                .map(Optional::get)
                                .map(Match::exact)
                                .collect(Collectors.toSet());

                        if (!tests.isEmpty()) {
                            return Resolution.matches(tests);
                        }
                    }

                    return Resolution.unresolved();
                }
            }
        """
        file("$basePath/src/main/java/org/gradle/testing/testengine/engine/CustomTestSourceDeclaringTestEngine.java") << """
            package org.gradle.testing.testengine.engine;

            import org.gradle.testing.testengine.descriptor.CustomSourceTestDescriptor;
            import org.junit.platform.commons.logging.Logger;
            import org.junit.platform.commons.logging.LoggerFactory;
            import org.junit.platform.engine.DiscoverySelector;
            import org.junit.platform.engine.EngineDiscoveryRequest;
            import org.junit.platform.engine.EngineExecutionListener;
            import org.junit.platform.engine.ExecutionRequest;
            import org.junit.platform.engine.TestDescriptor;
            import org.junit.platform.engine.TestEngine;
            import org.junit.platform.engine.TestExecutionResult;
            import org.junit.platform.engine.UniqueId;
            import org.junit.platform.engine.support.descriptor.EngineDescriptor;
            import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;

            import java.util.stream.Collectors;

            public class CustomTestSourceDeclaringTestEngine implements TestEngine {
                public static final Logger LOGGER = LoggerFactory.getLogger(CustomTestSourceDeclaringTestEngine.class);

                public static final String ENGINE_ID = "custom-test-source";
                public static final String ENGINE_NAME = "Resource Based Test Engine";

                @Override
                public String getId() {
                    return ENGINE_ID;
                }

                @Override
                public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
                    LOGGER.info(() -> {
                        String selectorsMsg = discoveryRequest.getSelectorsByType(DiscoverySelector.class).stream()
                            .map(Object::toString)
                            .collect(Collectors.joining("\\n\\t", "\\t", ""));
                        return "Discovering tests with engine: " + uniqueId + " using selectors:\\n" + selectorsMsg;
                    });

                    EngineDescriptor engineDescriptor = new EngineDescriptor(uniqueId, ENGINE_NAME);

                    EngineDiscoveryRequestResolver.builder()
                        .addSelectorResolver(new CustomResourceBasedSelectorResolver())
                        .build()
                        .resolve(discoveryRequest, engineDescriptor);

                    return engineDescriptor;
                }

                @Override
                public void execute(ExecutionRequest executionRequest) {
                    LOGGER.info(() -> "Executing tests with engine: " + executionRequest.getRootTestDescriptor().getUniqueId());

                    EngineExecutionListener listener = executionRequest.getEngineExecutionListener();
                    executionRequest.getRootTestDescriptor().getChildren().forEach(test -> {
                        if (test instanceof CustomSourceTestDescriptor) {
                            listener.executionStarted(test);
                            LOGGER.info(() -> "Executing resource-based test: " + test);
                            listener.executionFinished(test, TestExecutionResult.successful());
                        } else {
                            throw new IllegalStateException("Cannot execute test: " + test + " of type: " + test.getClass().getName());
                        }
                    });
                }
            }
        """
        file("$basePath/src/main/java/org/gradle/testing/testengine/descriptor/CustomSourceTestDescriptor.java") << """
            package org.gradle.testing.testengine.descriptor;

            import org.junit.platform.engine.TestSource;
            import org.junit.platform.engine.UniqueId;
            import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
            import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
            import org.junit.platform.engine.support.descriptor.CompositeTestSource;
            import org.junit.platform.engine.support.descriptor.DirectorySource;
            import org.junit.platform.engine.support.descriptor.FilePosition;
            import org.junit.platform.engine.support.descriptor.FileSource;
            import org.junit.platform.engine.support.descriptor.MethodSource;
            import org.junit.platform.engine.support.descriptor.PackageSource;

            import java.io.File;
            import java.util.Arrays;
            import java.util.Optional;

            public final class CustomSourceTestDescriptor extends AbstractTestDescriptor {
                private final File file;
                private final String name;

                public CustomSourceTestDescriptor(UniqueId parentId, File file, String name) {
                    super(parentId.append("testDefinitionFile", file.getName()).append("testDefinition", name), file.getName() + " - " + name);
                    this.file = file;
                    this.name = name;
                }

                @Override
                public Type getType() {
                    return Type.TEST;
                }

                @Override
                public boolean mayRegisterTests() {
                    return false;
                }

                @Override
                public Optional<TestSource> getSource() {
                    if ("noLocation".equals(name)) {
                        return Optional.empty();
                    } else if ("unknownLocation".equals(name)) {
                        return Optional.of(new CustomTestSource());
                    } else if ("fileLocationNoPos".equals(name)) {
                        return Optional.of(FileSource.from(getFile()));
                    } else if ("fileLocationOnlyLine".equals(name)) {
                        return Optional.of(FileSource.from(getFile(), FilePosition.from(1)));
                    } else if ("fileLocationLineAndCol".equals(name)) {
                        return Optional.of(FileSource.from(getFile(), FilePosition.from(1, 2)));
                    } else if ("directorySource".equals(name)) {
                        return Optional.of(DirectorySource.from(new File(System.getProperty("user.home"))));
                    } else if ("classpathResourceSourceNoPos".equals(name)) {
                        return Optional.of(ClasspathResourceSource.from("SomeClass"));
                    } else if ("classpathResourceSourceOnlyLine".equals(name)) {
                        return Optional.of(ClasspathResourceSource.from("SomeClass", FilePosition.from(1)));
                    } else if ("classpathResourceSourceLineAndCol".equals(name)) {
                        return Optional.of(ClasspathResourceSource.from("SomeClass", FilePosition.from(1, 2)));
                    } else if ("noArgMethodLocation".equals(name)) {
                        return Optional.of(MethodSource.from("SomeClass", "someMethod"));
                    } else if ("someArgMethodLocation".equals(name)) {
                        return Optional.of(MethodSource.from("SomeClass", "someMethod", "SomeArg"));
                    } else if ("packageLocation".equals(name)) {
                        return Optional.of(PackageSource.from("some.package"));
                    } else if ("unknownAndFileLocation".equals(name)) {
                        return Optional.of(CompositeTestSource.from(
                            Arrays.asList(
                                new CustomTestSource(),
                                FileSource.from(getFile())
                            )
                        ));
                    } else {
                        return Optional.of(FileSource.from(getFile()));
                    }
                }

                public File getFile() {
                    return file;
                }

                public String getName() {
                    return name;
                }

                @Override
                public String toString() {
                    return "Test[file=" + file.getName() + ", name=" + name + "]";
                }

                public static class CustomTestSource implements TestSource {

                }
            }
        """
    }


    protected void assertTestsFromAllDefinitionsExecuted() {
        testEvents {
            task(':test') {
                nested('Gradle Test Run :test') {
                    nested('Gradle Test Executor') {
                        test('Test SomeTestSpec.rbt - foo')
                        test('Test SomeTestSpec.rbt - bar')
                        test('Test subSomeOtherTestSpec.rbt - other')
                    }
                }
            }
        }
    }

    protected void assertTestsFromSecondDefinitionsExecuted() {
        testEvents {
            task(':test') {
                nested('Gradle Test Run :test') {
                    nested('Gradle Test Executor') {
                        test('Test subSomeOtherTestSpec.rbt - other')
                    }
                }
            }
        }
    }
}
