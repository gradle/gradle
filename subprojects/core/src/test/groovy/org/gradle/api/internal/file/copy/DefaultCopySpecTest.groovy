/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file.copy

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.UserClassFilePermissions
import org.gradle.api.file.FilePermissions
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultConfigurableFilePermissions
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.pattern.PatternMatcher
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.Actions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

class DefaultCopySpecTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())
    private fileCollectionFactory = TestFiles.fileCollectionFactory(testDir.testDirectory)
    private objectFactory = TestUtil.objectFactory()
    private instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private final DefaultCopySpec spec = new DefaultCopySpec(fileCollectionFactory, objectFactory, instantiator, TestFiles.patternSetFactory)

    private List<String> getTestSourceFileNames() {
        ['first', 'second']
    }

    private List<File> getAbsoluteTestSources() {
        testSourceFileNames.collect { new File(testDir.testDirectory, it) }
    }

    def "from List"() {
        when:
        spec.from(absoluteTestSources)

        then:
        spec.sourcePaths as List == [absoluteTestSources]
    }

    def "from Array"() {
        when:
        spec.from(absoluteTestSources as File[])

        then:
        spec.sourcePaths as List == absoluteTestSources
    }

    def "from with Closure"() {
        when:
        CopySpec child = spec.from(sources) {}

        then:
        !child.is(spec)
        unpackWrapper(child).sourcePaths as List == [sources]

        where:
        sources << ['source', ['source1', 'source2']]
    }

    def "default destination path for root spec"() {
        expect:
        spec.buildRootResolver().destPath == relativeDirectory()
    }

    def "into"() {
        when:
        spec.into destDir

        then:
        spec.buildRootResolver().destPath == relativeDirectory(*destPath)

        where:
        destDir              | destPath
        'spec'               | ['spec']
        '/'                  | [];
        { -> 'spec' }        | ['spec'];
        { -> return 'spec' } | ['spec']
    }

    def 'with Spec'() {
        given:
        def other1 = Stub(CopySpecInternal)
        def other2 = Stub(CopySpecInternal)

        when:
        spec.with other1, other2

        then:
        spec.sourcePaths.empty
        spec.children.size() == 2
    }

    def 'into with Closure'() {
        when:
        CopySpec child = spec.into('target') {}

        then:
        !child.is(spec)
        unpackWrapper(child).buildRootResolver().destPath == relativeDirectory('target')
    }

    def 'into with Action'() {
        when:
        CopySpec child = spec.into('target', new Action<CopySpec>() {
            @Override
            void execute(CopySpec copySpec) {}
        })

        then:
        !child.is(spec)
        unpackWrapper(child).buildRootResolver().destPath == relativeDirectory('target')
    }

    def 'filter with Closure'() {
        when:
        spec.filter { it.length() > 10 ? null : it }

        then:
        spec.copyActions.size() == 1
    }

    def 'filter with Transformer'() {
        when:
        spec.filter(new Transformer<String, String>() {
            @Override
            String transform(String input) {
                input.length() > 10 ? null : input
            }
        })

        then:
        spec.copyActions.size() == 1
    }

    def 'no arg filter'() {
        when:
        spec.filter(StripJavaComments)

        then:
        spec.copyActions.size() == 1
    }

    def 'arg filter'() {
        when:
        spec.filter(HeadFilter, lines: 15, skip: 2)

        then:
        spec.copyActions.size() == 1
    }

    def 'expand'() {
        when:
        spec.expand(version: '1.2', skip: 2)

        then:
        spec.copyActions.size() == 1
    }

    def 'expand with action'() {
        when:
        spec.expand(version: '1.2', skip: 2) {details ->
            details.escapeBackslash = true
        }

        then:
        spec.copyActions.size() == 1
    }

    def 'two filters'() {
        when:
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines: 15, skip: 2)

        then:
        spec.copyActions.size() == 2
    }

    def 'adds rename to actions'() {
        when:
        spec.rename(regexp, "replacement")

        then:
        spec.copyActions.size() == 1
        def (copyAction) = spec.copyActions
        copyAction instanceof RenamingCopyAction
        copyAction.transformer instanceof RegExpNameMapper

        where:
        regexp << ["regexp", /regexp/]
    }

    def 'adds rename Closure to actions'() {
        when:
        spec.rename {}

        then:
        spec.copyActions.size() == 1
        spec.copyActions[0] instanceof RenamingCopyAction
    }

    def 'add action'() {
        given:
        Action<CopySpec> action = Mock()

        when:
        spec.eachFile(action)

        then:
        spec.copyActions == [action]
    }

    def 'add Closure as action'() {
        when:
        spec.eachFile {}

        then:
        spec.copyActions.size() == 1
    }

    def 'matching creates appropriate action'() {
        when:
        spec.filesMatching 'root/**/a*', Actions.doNothing()

        then:
        spec.copyActions.size() == 1
        def (copyAction) = spec.copyActions
        copyAction instanceof MatchingCopyAction
        PatternMatcher matcher = copyAction.matcher

        ['/root/folder/abc', '/root/abc'].each {
            assertMatches matcher, it
        }

        ['/notRoot/abc', '/not/root/abc', 'root/bbc', 'notRoot/bbc'].each {
            assertMatches matcher.negate(), it
        }
    }

    def 'matching with multiple patterns creates appropriate action'() {
        when:
        spec.filesMatching(['root/**/a*', 'special/*', 'banner.txt'], Actions.doNothing())

        then:
        spec.copyActions.size() == 1
        def (copyAction) = spec.copyActions
        copyAction instanceof MatchingCopyAction
        PatternMatcher matcher = copyAction.matcher

        ['/root/folder/abc', '/root/abc', 'special/foo', 'banner.txt'].each {
            assertMatches matcher, it
        }

        ['/notRoot/abc', '/not/root/abc', 'root/bbc', 'notRoot/bbc', 'not/special/bar'].each {
            assertMatches matcher.negate(), it
        }
    }

    def 'notMatching creates appropriate action'() {
        when:
        spec.filesNotMatching('**/a*/**', Actions.doNothing())

        then:
        spec.copyActions.size() == 1
        def (copyAction) = spec.copyActions
        copyAction instanceof MatchingCopyAction
        PatternMatcher matcher = copyAction.matcher

        ['root/folder1/folder2', 'modules/project1'].each {
            assertMatches matcher, it
        }

        ['archive/folder/file', 'root/archives/file', 'root/folder/abc'].each {
            assertMatches matcher.negate(), it
        }
    }

    def 'notMatching multiple Patterns creates appropriate action'() {
        when:
        spec.filesNotMatching(['**/a*/**', '**/c*/**'], Actions.doNothing())

        then:
        spec.copyActions.size() == 1
        def (copyAction) = spec.copyActions
        copyAction instanceof MatchingCopyAction
        PatternMatcher matcher = copyAction.matcher

        ['root/folder1/folder2', 'modules/project1'].each {
            assertMatches matcher, it
        }

        ['archive/folder/file', 'root/archives/file', 'root/folder/abc',
         'collections/folder/file', 'root/collections/file', 'archives/collections/file',
         'root/folder/cde'].each {
            assertMatches matcher.negate(), it
        }
    }

    private static void assertMatches(PatternMatcher matcher, String path) {
        def file = relativeFile(path)
        assert matcher.test(file.segments, file.isFile())
    }

    def 'add Spec as first child'() {
        when:
        DefaultCopySpec child1 = spec.addFirst()

        then:
        child1
        spec.children == [child1]

        when:
        DefaultCopySpec child2 = spec.addFirst()

        then:
        child2
        spec.children == [child2, child1]
    }

    def 'add Spec in between two child Specs if given child exists'() {
        when:
        DefaultCopySpec child1 = spec.addChild()
        DefaultCopySpec child2 = spec.addChild()

        then:
        child1
        child2
        spec.children == [child1, child2]

        when:
        DefaultCopySpec child3 = spec.addChildBeforeSpec(child2)

        then:
        child3
        spec.children == [child1, child3, child2]
    }

    def 'append Spec after two child Specs if given child does not exist or is null'() {
        when:
        DefaultCopySpec child1 = spec.addChild()
        DefaultCopySpec child2 = spec.addChild()

        then:
        child1
        child2
        spec.children == [child1, child2]

        when:
        DefaultCopySpec child3 = spec.addChildBeforeSpec(notContainedChild)

        then:
        child3
        spec.children == [child1, child2, child3]

        where:
        notContainedChild << [null, Stub(CopySpecInternal)]
    }

    def 'properties accessed directly have defaults'() {
        expect:
        spec.caseSensitive
        spec.includeEmptyDirs
        spec.duplicatesStrategy == DuplicatesStrategy.INCLUDE
        spec.fileMode == null
        !spec.filePermissions.isPresent()
        spec.dirMode == null
        !spec.dirPermissions.isPresent()
        spec.filteringCharset == Charset.defaultCharset().name()
    }

    def 'file permissions can be set via #method'(String method, Closure setter) {
        when:
        spec.caseSensitive = false
        spec.includeEmptyDirs = false
        spec.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        setter.call(spec, objectFactory)
        spec.filteringCharset = 'UTF8'

        then:
        !spec.caseSensitive
        !spec.includeEmptyDirs
        spec.duplicatesStrategy == DuplicatesStrategy.EXCLUDE
        spec.fileMode == 0444
        toPermissionString(spec.filePermissions.get()) == "r--r--r--"
        spec.dirMode == 0655
        toPermissionString(spec.dirPermissions.get()) == "rw-r-xr-x"
        spec.filteringCharset == 'UTF8'

        where:
        method             | setter
        "mode"                  | { DefaultCopySpec spec, ObjectFactory objectFactory ->
            spec.fileMode = 0444
            spec.dirMode = 0655
        }
        "property"              | { DefaultCopySpec spec, ObjectFactory objectFactory ->
            spec.filePermissions.value(new DefaultConfigurableFilePermissions(objectFactory, 0444))
            spec.dirPermissions.value(new DefaultConfigurableFilePermissions(objectFactory, 0655))
        }
        "configuration block"   | { DefaultCopySpec spec, ObjectFactory objectFactory ->
            spec.filePermissions {
                it.user.write = false
            }
            spec.dirPermissions {
                it.user.execute = false
            }
        }
    }

    def 'properties accessed directly on specs created using #method inherit from parents'() {
        when: //set non defaults on root
        spec.caseSensitive = false
        spec.includeEmptyDirs = false
        spec.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        spec.fileMode = 1
        spec.dirMode = 2
        spec.filteringCharset = "ISO_8859_1"

        DefaultCopySpec child = unpackWrapper(spec."${method}"("child") {})

        then: //children still have these non defaults
        !child.caseSensitive
        !child.includeEmptyDirs
        child.duplicatesStrategy == DuplicatesStrategy.EXCLUDE
        child.fileMode == 1
        child.dirMode == 2
        child.filteringCharset == "ISO_8859_1"

        where:
        method << ['from', 'into']
    }

    def 'setting the filteringCharset to invalid value throws an exception'() {
        when:
        spec.filteringCharset = "THAT_SURE_IS_AN_INVALID_CHARSET"

        then:
        thrown(InvalidUserDataException)
    }

    def 'setting the filteringCharset to null throws an exception'() {
        when:
        spec.filteringCharset = null

        then:
        thrown(NullPointerException)
    }

    def 'can add spec hierarchy as child'() {
        CopySpec otherSpec = new DefaultCopySpec(fileCollectionFactory, objectFactory, instantiator, TestFiles.patternSetFactory)
        otherSpec.addChild()
        def added = []

        spec.addChildSpecListener { CopySpecInternal.CopySpecAddress path, CopySpecInternal spec ->
            added.add path.toString()
        }

        when:
        spec.addChild()

        then:
        added == ['$1']

        when:
        added.clear()
        spec.with otherSpec

        then:
        added == ['$2', '$2$1']

        when:
        added.clear()
        otherSpec.addChild().addChild()

        then:
        added == ['$2$2', '$2$2$1']
    }

    private static DefaultCopySpec unpackWrapper(CopySpec copySpec) {
        (copySpec as CopySpecWrapper).delegate as DefaultCopySpec
    }

    private static RelativePath relativeDirectory(String... segments) {
        new RelativePath(false, segments)
    }

    private static RelativePath relativeFile(String segments) {
        RelativePath.parse(true, segments)
    }

    static String toPermissionString(FilePermissions permissions) {
        def user = toPermissionString(permissions.user)
        def group = toPermissionString(permissions.group)
        def other = toPermissionString(permissions.other)
        return user + group  + other
    }

    static String toPermissionString(UserClassFilePermissions permission) {
        def read = permission.read ? "r" : "-"
        def write = permission.write ? "w" : "-"
        def execute = permission.execute ? "x" : "-"
        return read + write + execute
    }

}
