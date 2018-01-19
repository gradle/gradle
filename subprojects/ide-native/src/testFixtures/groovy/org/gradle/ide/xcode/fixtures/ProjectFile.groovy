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
import com.google.common.base.Objects
import org.gradle.ide.xcode.internal.xcodeproj.PBXTarget.ProductType
import org.gradle.test.fixtures.file.TestFile

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

        def getProperty(String name) {
            def value = object.get(name)
            if (isId(value)) {
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
            Objects.toStringHelper(this)
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

        List<PBXObject> getChildren() {
            return getProperty("children")
        }

        def assertHasChildren(List<String> entries) {
            def children = getProperty("children")
            assert children.size() == entries.size()
            assert children*.name.containsAll(entries)
            return true
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

        PBXObject getProductReference() {
            return getProperty("productReference")
        }

        void assertIsTool() {
            assertIs(ProductType.TOOL)
        }

        void assertIsDynamicLibrary() {
            assertIs(ProductType.DYNAMIC_LIBRARY)
        }

        void assertIsStaticLibrary() {
            assertIs(ProductType.STATIC_LIBRARY)
        }

        void assertIsUnitTest() {
            assert isUnitTest()
        }

        void assertIs(ProductType productType) {
            assert is(productType)
        }

        boolean isUnitTest() {
            return is(ProductType.UNIT_TEST)
        }

        boolean is(ProductType productType) {
            return getProperty("productType") == productType.identifier
        }

        @Override
        String toString() {
            Objects.toStringHelper(this)
                .add('name', getName())
                .add('productName', getProductName())
                .toString()
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
