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

    PBXLegacyTarget getGradleTarget() {
        return targets.find { it instanceof PBXLegacyTarget }
    }

    PBXGroup getMainGroup() {
        return rootObject.getProperty("mainGroup")
    }

    PBXGroup getProducts() {
        return mainGroup.children.find { it.name == 'Products' }
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
        assert targets.every { it.productType == productType.identifier }
    }

    private <T extends PBXObject> T toPbxObject(String id) {
        NSDictionary object = (NSDictionary)getObjects().get(id)

        if (object.isa.toJavaObject() == "PBXGroup") {
            return new PBXGroup(id, object)
        } else if (object.isa.toJavaObject() == "PBXLegacyTarget") {
            return new PBXLegacyTarget(id, object)
        } else if (object.isa.toJavaObject() == "PBXNativeTarget") {
            return new PBXNativeTarget(id, object)
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
            // Check if the value is a FB generated id (static 24 chars) or Gradle generated id (static 36 chars - uuid)
            return obj instanceof NSString && (obj.getContent().length() == 24 || obj.getContent().length() == 36)
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

        PBXObject getProductReference() {
            return getProperty("productReference")
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
