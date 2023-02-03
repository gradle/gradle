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

package org.gradle.ide.xcode.fixtures

import com.dd.plist.NSArray
import com.dd.plist.NSDictionary
import com.dd.plist.NSObject
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import com.google.common.base.MoreObjects
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget.ProductType
import org.gradle.test.fixtures.file.TestFile

import javax.annotation.Nullable

class ProjectFile {
    private final TestFile file
    final NSDictionary content
    private Map<String, NSObject> objects
    private PBXObject rootObject

    ProjectFile(TestFile pbxProjectFile) {
        pbxProjectFile.assertIsFile()
        file = pbxProjectFile
        content = PropertyListParser.parse(file)
        objects = ((NSDictionary)content.get("objects")).getHashMap()
        rootObject = toPbxObject(toNSString(content.get("rootObject")).getContent())
    }

    protected Map<String, NSObject> getObjects() {
        return objects
    }

    TestFile getFile() {
        return file
    }

    PBXObject getBuildConfigurationList() {
        return rootObject.getProperty("buildConfigurationList")
    }

    List<PBXTarget> getTargets() {
        return rootObject.getProperty("targets")
    }

    PBXNativeTarget getIndexTarget() {
        return targets.find { it instanceof PBXNativeTarget }
    }

    List<PBXTarget> findTargets(String moduleName) {
        return targets.findAll { it.name == moduleName }
    }

    PBXLegacyTarget getGradleTarget() {
        return targets.find { it instanceof PBXLegacyTarget }
    }

    PBXGroup getMainGroup() {
        return rootObject.getProperty("mainGroup")
    }

    PBXGroup getProducts() {
        return findGroup('Products')
    }

    PBXGroup getSources() {
        return findGroup('Sources')
    }

    PBXGroup getTests() {
        return findGroup('Tests')
    }

    PBXGroup getHeaders() {
        return findGroup('Headers')
    }

    private PBXGroup findGroup(String groupName) {
        return mainGroup.children.find { it.name == groupName }
    }

    void assertNoTargets() {
        assert targets.empty
    }

    void assertTargetsAreTools() {
        assertTargetsAre(ProductType.TOOL)
    }

    void assertTargetsAreDynamicLibraries() {
        assertTargetsAre(ProductType.DYNAMIC_LIBRARY)
    }

    void assertTargetsAre(ProductType productType) {
        targets.each { it.assertIs(productType) }
    }

    private <T extends PBXObject> T toPbxObject(String id) {
        NSDictionary object = (NSDictionary)getObjects().get(id)

        if (object.isa.toJavaObject() == "PBXGroup") {
            return new PBXGroup(id, object)
        } else if (object.isa.toJavaObject() == "PBXLegacyTarget") {
            return new PBXLegacyTarget(id, object)
        } else if (object.isa.toJavaObject() == "PBXNativeTarget") {
            return new PBXNativeTarget(id, object)
        } else if (object.isa.toJavaObject() == "XCBuildConfiguration") {
            return new XCBuildConfiguration(id, object)
        } else {
            return new PBXObject(id, object)
        }
    }

    private static NSString toNSString(NSObject object) {
        return (NSString)object
    }

    class PBXObject {
        final String id
        private final NSDictionary object

        PBXObject(String id, NSDictionary object) {
            this.id = id
            this.object = object
        }

        @Nullable
        def getProperty(String name) {
            def value = object.get(name)
            if (value == null) {
                return null
            } else if (isId(value)) {
                return toPbxObject(toNSString(value).getContent())
            } else if (value instanceof NSArray) {
                def list = []
                for (NSObject obj : value.getArray()) {
                    if (isId(obj)) {
                        list.add(toPbxObject(toNSString(obj).getContent()))
                    } else {
                        list.add(obj)
                    }
                }
                return list
            }
            return value.toJavaObject()
        }

        private static boolean isId(NSObject obj) {
            // Check if the value is a FB generated id (static 24 chars)
            if (obj instanceof NSString && (obj.getContent().length() == 24)) {
                return obj.getContent().toCharArray().every {
                    Character.isDigit(it) || Character.isUpperCase(it)
                }
            }
            return false
        }

        @Override
        String toString() {
            MoreObjects.toStringHelper(this)
                .add('isa', getProperty("isa"))
                .toString()
        }
    }

    class XCBuildConfiguration extends PBXObject {
        XCBuildConfiguration(String id, NSDictionary object) {
            super(id, object)
        }

        Map<String, String> getBuildSettings() {
            def map = [:]
            getProperty("buildSettings").entrySet().each {
                map.put(it.key, toNSString(it.value).getContent())
            }
            return map
        }
    }

    class PBXGroup extends PBXObject {
        PBXGroup(String id, NSDictionary object) {
            super(id, object)
        }

        String getName() {
            return getProperty("name")
        }

        List<PBXObject> getChildren() {
            return getProperty("children")
        }

        def assertHasChildren(List<String> entries) {
            def children = getProperty("children")
            assert children.size() == entries.size()
            assert children*.name.containsAll(entries)
            return true
        }

        @Override
        String toString() {
            MoreObjects.toStringHelper(this)
                    .add('name', getName())
                    .toString()
        }
    }

    class PBXTarget extends PBXObject {
        PBXTarget(String id, NSDictionary object) {
            super(id, object)
        }

        String getName() {
            return getProperty("name")
        }

        String getProductName() {
            return getProperty("productName")
        }

        @Nullable
        PBXObject getProductReference() {
            return getProperty("productReference")
        }

        void assertProductNameEquals(String expectedProductName) {
            assert this.productName == expectedProductName
            assert this.buildConfigurationList.buildConfigurations.every { it.buildSettings.PRODUCT_NAME == expectedProductName }
        }

        ProductType getProductType() {
            return ProductType.values().find { it.identifier == getProperty("productType")}
        }

        void assertIsTool() {
            assertIs(ProductType.TOOL)
            this.buildConfigurationList.buildConfigurations.each { ProjectFile.PBXTarget.assertNotUnitTestBuildSettings(it.buildSettings) }
        }

        void assertIsDynamicLibrary() {
            assertIs(ProductType.DYNAMIC_LIBRARY)
            this.buildConfigurationList.buildConfigurations.each { ProjectFile.PBXTarget.assertNotUnitTestBuildSettings(it.buildSettings) }
        }

        void assertIsStaticLibrary() {
            assertIs(ProductType.STATIC_LIBRARY)
            this.buildConfigurationList.buildConfigurations.each { ProjectFile.PBXTarget.assertNotUnitTestBuildSettings(it.buildSettings) }
        }

        void assertIsUnitTest() {
            assertIs(ProductType.UNIT_TEST)
            this.buildConfigurationList.buildConfigurations.each {
                if (it.name.startsWith("__GradleTestRunner_")) {
                    ProjectFile.PBXTarget.assertUnitTestBuildSettings(it.buildSettings)
                } else {
                    ProjectFile.PBXTarget.assertNotUnitTestBuildSettings(it.buildSettings)
                }
            }
        }

        private static void assertNotUnitTestBuildSettings(Map<String, String> buildSettings) {
            assert buildSettings.OTHER_CFLAGS == null
            assert buildSettings.OTHER_LDFLAGS == null
            assert buildSettings.OTHER_SWIFT_FLAGS == null
            assert buildSettings.SWIFT_INSTALL_OBJC_HEADER == null
            assert buildSettings.SWIFT_OBJC_INTERFACE_HEADER_NAME == null
        }

        private static void assertUnitTestBuildSettings(Map<String, String> buildSettings) {
            assert buildSettings.OTHER_CFLAGS == "-help"
            assert buildSettings.OTHER_LDFLAGS == "-help"
            assert buildSettings.OTHER_SWIFT_FLAGS == "-help"
            assert buildSettings.SWIFT_INSTALL_OBJC_HEADER == "NO"
            assert buildSettings.SWIFT_OBJC_INTERFACE_HEADER_NAME == "\$(PRODUCT_NAME).h"
        }

        void assertIs(ProductType productType) {
            assert is(productType)
        }

        boolean isUnitTest() {
            return is(ProductType.UNIT_TEST)
        }

        boolean is(ProductType productType) {
            return getProductType() == productType
        }

        @Override
        String toString() {
            MoreObjects.toStringHelper(this)
                .add('name', getName())
                .add('productName', getProductName())
                .add('productType', getProductType())
                .toString()
        }

        void assertSupportedArchitectures(String... architectures) {
            def toXcodeArchitecture = [x86: 'i386', 'x86-64': 'x86_64', aarch64: 'arm64e'].withDefault { it }
            String expectedValidArchitectures = architectures.collect { toXcodeArchitecture.get(it) }.join(" ")
            assert this.buildConfigurationList.buildConfigurations.every { it.buildSettings.VALID_ARCHS == expectedValidArchitectures }
        }

        void assertIsIndexerFor(PBXTarget target) {
            assertIsIndexer()
            assert this.name == "[INDEXING ONLY] ${target.name}"
            this.assertProductNameEquals(target.productName)
            int expectedBuildConfigurationsCount = target.buildConfigurationList.buildConfigurations.size()
            if (target.isUnitTest()) {
                expectedBuildConfigurationsCount--
            }
            assert this.buildConfigurationList.buildConfigurations.size() == expectedBuildConfigurationsCount
            this.buildConfigurationList.buildConfigurations.eachWithIndex { buildConfiguration, idx ->
                assert buildConfiguration.name == target.buildConfigurationList.buildConfigurations[idx].name
                assert buildConfiguration.buildSettings.ARCHS == target.buildConfigurationList.buildConfigurations[idx].buildSettings.ARCHS
                assert buildConfiguration.buildSettings.VALID_ARCHS == target.buildConfigurationList.buildConfigurations[idx].buildSettings.VALID_ARCHS
            }
        }

        void assertIsIndexer() {
            assertIs(ProductType.INDEXER)
            assert this.name.startsWith("[INDEXING ONLY] ")
            this.buildConfigurationList.buildConfigurations.each { buildConfiguration ->
                ProjectFile.PBXTarget.assertNotUnitTestBuildSettings(buildConfiguration.buildSettings)
            }
        }
    }

    class PBXNativeTarget extends PBXTarget {
        PBXNativeTarget(String id, NSDictionary object) {
            super(id, object)
        }

        Map<String, ?> getBuildSettings() {
            return buildConfigurationList.buildConfigurations[0].buildSettings
        }
    }

    class PBXLegacyTarget extends PBXTarget {
        PBXLegacyTarget(String id, NSDictionary object) {
            super(id, object)
        }
    }
}
