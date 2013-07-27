/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.descriptor.*
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.GlobPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.junit.Assert.*

class IvyXmlModuleDescriptorParserTest extends Specification {
    @Rule
    public final Resources resources = new Resources()
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    IvyXmlModuleDescriptorParser parser = new IvyXmlModuleDescriptorParser()
    IvySettings settings = new IvySettings()

    def setup() {
        settings.setDefaultCache(temporaryFolder.createDir("ivy/cache"))
    }

    def "parses Ivy descriptor with empty dependencies section"() throws Exception {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
          status="integration"
          publication="20041101110000"
    />
    <dependencies>
    </dependencies>
</ivy-module>
"""
        ModuleDescriptor md = parser.parseDescriptor(settings, file, true)

        then:
        md != null
        "myorg" == md.getModuleRevisionId().getOrganisation()
        "mymodule" == md.getModuleRevisionId().getName()
        "myrev" == md.getModuleRevisionId().getRevision()
        "integration" == md.getStatus()
        md.getConfigurations() != null
        Arrays.asList(new Configuration("default")) == Arrays.asList(md.getConfigurations())
        md.getArtifacts("default") != null
        1 == md.getArtifacts("default").length
        "mymodule" == md.getArtifacts("default")[0].getName()
        "jar" == md.getArtifacts("default")[0].getType()
        md.getDependencies() != null
        0 == md.getDependencies().length
    }

    public void "fails when configuration extends an unknown configuration"() throws IOException {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A" extends="invalidConf"/>
    </configurations>
</ivy-module>
"""

        when:
        parser.parseDescriptor(settings, file, true)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file.toURI()}"
        e.cause.message == "unknown configuration 'invalidConf'. It is extended by A"
    }

    public void "fails when there is a cycle in configuration hierarchy"() throws IOException {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A" extends="B"/>
        <conf name="B" extends="A"/>
    </configurations>
</ivy-module>
"""

        when:
        parser.parseDescriptor(settings, file, true)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file.toURI()}"
        e.cause.message == "illegal cycle detected in configuration extension: A => B => A"
    }

    public void "fails when descriptor contains badly formed XML"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info
</ivy-module>
"""

        when:
        parser.parseDescriptor(settings, file, true)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file.toURI()}"
        e.cause.message.contains('Element type "info"')
    }

    public void "fails when descriptor does not match schema"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <not-an-ivy-file/>
</ivy-module>
"""

        when:
        parser.parseDescriptor(settings, file, true)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file.toURI()}"
        e.cause.message.contains('unknown tag not-an-ivy-file')
    }

    public void "parses a full Ivy descriptor"() throws Exception {
        def file = temporaryFolder.file("ivy.xml")
        file.text = resources.getResource("test-full.xml").text

        when:
        ModuleDescriptor md = parser.parseDescriptor(settings, file, false)

        then:
        assertNotNull(md)
        assertEquals("myorg", md.getModuleRevisionId().getOrganisation())
        assertEquals("mymodule", md.getModuleRevisionId().getName())
        assertEquals("myrev", md.getModuleRevisionId().getRevision())
        assertEquals("integration", md.getStatus())
        Date pubdate = new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime()
        assertEquals(pubdate, md.getPublicationDate())

        License[] licenses = md.getLicenses()
        assertEquals(1, licenses.length)
        assertEquals("MyLicense", licenses[0].getName())
        assertEquals("http://www.my.org/mymodule/mylicense.html", licenses[0].getUrl())

        assertEquals("http://www.my.org/mymodule/", md.getHomePage())
        assertEquals("This module is <b>great</b> !<br/>\n\t"
                + "You can use it especially with myconf1 and myconf2, "
                + "and myconf4 is not too bad too.",
                md.getDescription().replaceAll("\r\n", "\n").replace('\r', '\n'))

        assertEquals(1, md.getExtraInfo().size())
        assertEquals("56576", md.getExtraInfo().get("e:someExtra"))

        Configuration[] confs = md.getConfigurations()
        assertNotNull(confs)
        assertEquals(5, confs.length)

        assertConf(md, "myconf1", "desc 1", Configuration.Visibility.PUBLIC, new String[0])
        assertConf(md, "myconf2", "desc 2", Configuration.Visibility.PUBLIC, new String[0])
        assertConf(md, "myconf3", "desc 3", Configuration.Visibility.PRIVATE, new String[0])
        assertConf(md, "myconf4", "desc 4", Configuration.Visibility.PUBLIC, ["myconf1", "myconf2"].toArray(new String[2]))
        assertConf(md, "myoldconf", "my old desc", Configuration.Visibility.PUBLIC, new String[0])

        assertArtifacts(md.getArtifacts("myconf1"), ["myartifact1", "myartifact2", "myartifact3", "myartifact4"].toArray(new String[4]))

        assertArtifacts(md.getArtifacts("myconf2"), ["myartifact1", "myartifact3"].toArray(new String[2]))
        assertArtifacts(md.getArtifacts("myconf3"), ["myartifact1", "myartifact3", "myartifact4"].toArray(new String[3]))
        assertArtifacts(md.getArtifacts("myconf4"), ["myartifact1"].toArray(new String[1]))

        DependencyDescriptor[] dependencies = md.getDependencies()
        assertNotNull(dependencies)
        assertEquals(13, dependencies.length)

        verifyFullDependencies(dependencies)

        ExcludeRule[] rules = md.getAllExcludeRules()
        assertNotNull(rules)
        assertEquals(2, rules.length)
        assertEquals(GlobPatternMatcher.INSTANCE, rules[0].getMatcher())
        assertEquals(ExactPatternMatcher.INSTANCE, rules[1].getMatcher())
        assertEquals(Arrays.asList("myconf1"), Arrays.asList(rules[0]
                .getConfigurations()))
        assertEquals(Arrays.asList("myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"), Arrays.asList(rules[1].getConfigurations()))
        true
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2766")
    def "defaultconfmapping is respected"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      status="integration"
                      publication="20041101110000">
                </info>
                <configurations>
                    <conf name="myconf" />
                </configurations>
                <publications/>
                <dependencies defaultconfmapping="myconf->default">
                    <dependency name="mymodule2"/>
                </dependencies>
            </ivy-module>
        """

        when:
        def descriptor = parser.parseDescriptor(settings, file, false)
        def dependency = descriptor.dependencies.first()

        then:
        dependency.moduleConfigurations == ["myconf"]
    }

    def "parses dependency config mappings"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" />
                </configurations>
                <publications/>
                <dependencies>
                    <dependency name="mymodule2" conf="a"/>
                    <dependency name="mymodule2" conf="a->other"/>
                    <dependency name="mymodule2" conf="*->@"/>
                    <dependency name="mymodule2" conf="a->other;%->@"/>
                    <dependency name="mymodule2" conf="*,!a->@"/>
                    <dependency name="mymodule2" conf="a->*"/>
                    <dependency name="mymodule2" conf="a->one,two;a,b->three;*->four;%->none"/>
                    <dependency name="mymodule2" conf="a->#"/>
                    <dependency name="mymodule2" conf="a->a;%->@"/>
                    <dependency name="mymodule2" conf="a->a;*,!a->b"/>
                </dependencies>
            </ivy-module>
        """

        when:
        def descriptor = parser.parseDescriptor(settings, file, false)

        then:
        def dependency1 = descriptor.dependencies[0]
        dependency1.moduleConfigurations == ["a"]
        dependency1.getDependencyConfigurations("a") == ["a"]
        dependency1.getDependencyConfigurations("a", "requested") == ["a"]

        def dependency2 = descriptor.dependencies[1]
        dependency2.moduleConfigurations == ["a"]
        dependency2.getDependencyConfigurations("a") == ["other"]
        dependency2.getDependencyConfigurations("a", "requested") == ["other"]

        def dependency3 = descriptor.dependencies[2]
        dependency3.moduleConfigurations == ["*"]
        dependency3.getDependencyConfigurations("a") == ["a"]
        dependency3.getDependencyConfigurations("a", "requested") == ["a"]

        def dependency4 = descriptor.dependencies[3]
        dependency4.moduleConfigurations == ["a", "%"]
        dependency4.getDependencyConfigurations("a") == ["other"]
        dependency4.getDependencyConfigurations("a", "requested") == ["other"]
        dependency4.getDependencyConfigurations("b") == ["b"]
        dependency4.getDependencyConfigurations("b", "requested") == ["b"]

        def dependency5 = descriptor.dependencies[4]
        dependency5.moduleConfigurations == ["*", "!a"]
        dependency5.getDependencyConfigurations("a") == ["a"]
        dependency5.getDependencyConfigurations("a", "requested") == ["a"]

        def dependency6 = descriptor.dependencies[5]
        dependency6.moduleConfigurations == ["a"]
        dependency6.getDependencyConfigurations("a") == ["*"]
        dependency6.getDependencyConfigurations("a", "requested") == ["*"]

        def dependency7 = descriptor.dependencies[6]
        dependency7.moduleConfigurations == ["a", "b", "*", "%"]
        dependency7.getDependencyConfigurations("a") == ["one", "two", "three", "four"]
        dependency7.getDependencyConfigurations("b") == ["three", "four"]
        dependency7.getDependencyConfigurations("c") == ["none", "four"]

        def dependency8 = descriptor.dependencies[7]
        dependency8.moduleConfigurations == ["a"]
        dependency8.getDependencyConfigurations("a") == ["a"]
        dependency8.getDependencyConfigurations("a", "requested") == ["requested"]

        def dependency9 = descriptor.dependencies[8]
        dependency9.moduleConfigurations == ["a", "%"]
        dependency9.getDependencyConfigurations("a") == ["a"]
        dependency9.getDependencyConfigurations("a", "requested") == ["a"]
        dependency9.getDependencyConfigurations("b") == ["b"]
        dependency9.getDependencyConfigurations("b", "requested") == ["b"]

        def dependency10 = descriptor.dependencies[9]
        dependency10.moduleConfigurations == ["a", "*", "!a"]
        dependency10.getDependencyConfigurations("a") == ["a", "b"]
        dependency10.getDependencyConfigurations("a", "requested") == ["a", "b"]
        dependency10.getDependencyConfigurations("b") == ["b"]
        dependency10.getDependencyConfigurations("b", "requested") == ["b"]
    }

    def verifyFullDependencies(DependencyDescriptor[] dependencies) {
        // no conf def => equivalent to *->*
        DependencyDescriptor dd = getDependency(dependencies, "mymodule2")
        assertNotNull(dd)
        assertEquals("myorg", dd.getDependencyId().getOrganisation())
        assertEquals("2.0", dd.getDependencyRevisionId().getRevision())
        assertEquals(["*"], Arrays.asList(dd.getModuleConfigurations()))
        assertEquals(["*"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals(["*"], Arrays.asList(dd.getDependencyConfigurations(["myconf2", "myconf3", "myconf4"].toArray(new String[3]))))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])
        assertFalse(dd.isChanging())
        assertTrue(dd.isTransitive())

        // changing = true
        dd = getDependency(dependencies, "mymodule3")
        assertNotNull(dd)
        assertTrue(getDependency(dependencies, "mymodule3").isChanging())
        assertFalse(getDependency(dependencies, "mymodule3").isTransitive())
        // conf="myconf1" => equivalent to myconf1->myconf1
        dd = getDependency(dependencies, "yourmodule1")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("trunk", dd.getDependencyRevisionId().getBranch())
        assertEquals("1.1", dd.getDependencyRevisionId().getRevision())
        assertEquals("branch1", dd.getDynamicConstraintDependencyRevisionId().getBranch())
        assertEquals("1+", dd.getDynamicConstraintDependencyRevisionId().getRevision())
        assertEquals("yourorg#yourmodule1#branch1;1+", dd.getDynamicConstraintDependencyRevisionId().toString())

        assertEquals(["myconf1"], Arrays.asList(dd.getModuleConfigurations()))
        assertEquals(["myconf1"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf2", "myconf3", "myconf4"].toArray(new String[3]))))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        // conf="myconf1->yourconf1"
        dd = getDependency(dependencies, "yourmodule2")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("2+", dd.getDependencyRevisionId().getRevision())
        assertEquals(["myconf1"], Arrays.asList(dd.getModuleConfigurations()))
        assertEquals(["yourconf1"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf2", "myconf3", "myconf4"].toArray(new String[3]))))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        // conf="myconf1->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule3")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("3.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(["myconf1"], Arrays.asList(dd.getModuleConfigurations()))
        assertEquals(["yourconf1", "yourconf2"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf2", "myconf3", "myconf4"].toArray(new String[3]))))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        // conf="myconf1, myconf2->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule4")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("4.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(new HashSet(["myconf1", "myconf2"]), new HashSet(
                Arrays.asList(dd.getModuleConfigurations())))
        assertEquals(["yourconf1", "yourconf2"], Arrays.asList(dd
                .getDependencyConfigurations("myconf1")))
        assertEquals(["yourconf1", "yourconf2"], Arrays.asList(dd
                .getDependencyConfigurations("myconf2")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf3", "myconf4"])))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        // conf="myconf1->yourconf1myconf2->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule5")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("5.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(["myconf1", "myconf2"] as Set, new HashSet(Arrays.asList(dd.getModuleConfigurations())))
        assertEquals(["yourconf1"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals(["yourconf1", "yourconf2"], Arrays.asList(dd.getDependencyConfigurations("myconf2")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf3", "myconf4"])))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        // conf="*->@"
        dd = getDependency(dependencies, "yourmodule11")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("11.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(["*"] as Set, new HashSet(Arrays.asList(dd.getModuleConfigurations())))
        assertEquals(["myconf1"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals(["myconf2"], Arrays.asList(dd.getDependencyConfigurations("myconf2")))
        assertEquals(["myconf3"], Arrays.asList(dd.getDependencyConfigurations("myconf3")))
        assertEquals(["myconf4"], Arrays.asList(dd.getDependencyConfigurations("myconf4")))

        dd = getDependency(dependencies, "yourmodule6")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("latest.integration", dd.getDependencyRevisionId().getRevision())
        assertEquals(["myconf1", "myconf2"] as Set, new HashSet(Arrays.asList(dd.getModuleConfigurations())))
        assertEquals(["yourconf1"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals(["yourconf1", "yourconf2"], Arrays.asList(dd.getDependencyConfigurations("myconf2")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf3", "myconf4"])))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        dd = getDependency(dependencies, "yourmodule7")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("7.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(new HashSet(["myconf1", "myconf2"]), new HashSet(
                Arrays.asList(dd.getModuleConfigurations())))
        assertEquals(["yourconf1"], Arrays.asList(dd.getDependencyConfigurations("myconf1")))
        assertEquals(["yourconf1", "yourconf2"], Arrays.asList(dd.getDependencyConfigurations("myconf2")))
        assertEquals([], Arrays.asList(dd.getDependencyConfigurations(["myconf3", "myconf4"])))
        assertDependencyArtifactIncludeRules(dd, ["myconf1", "myconf2", "myconf3", "myconf4"], [])

        dd = getDependency(dependencies, "yourmodule8")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("8.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(["*"] as Set, new HashSet(Arrays.asList(dd.getModuleConfigurations())))
        assertDependencyArtifacts(dd, ["myconf1"], ["yourartifact8-1", "yourartifact8-2"])
        assertDependencyArtifacts(dd, ["myconf2"], ["yourartifact8-1", "yourartifact8-2"])
        assertDependencyArtifacts(dd, ["myconf3"], ["yourartifact8-1", "yourartifact8-2"])
        assertDependencyArtifacts(dd, ["myconf4"], ["yourartifact8-1", "yourartifact8-2"])
        dd = getDependency(dependencies, "yourmodule9")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("9.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(["myconf1", "myconf2", "myconf3"] as Set, new HashSet(Arrays.asList(dd.getModuleConfigurations())))
        assertDependencyArtifacts(dd, ["myconf1"], ["yourartifact9-1"])
        assertDependencyArtifacts(dd, ["myconf2"], ["yourartifact9-1", "yourartifact9-2"])
        assertDependencyArtifacts(dd, ["myconf3"], ["yourartifact9-2"])
        assertDependencyArtifacts(dd, ["myconf4"], [])
        assertDependencyArtifactExcludeRules(dd, ["myconf1"], [])
        assertDependencyArtifactExcludeRules(dd, ["myconf2"], [])
        assertDependencyArtifactExcludeRules(dd, ["myconf3"], [])
        assertDependencyArtifactExcludeRules(dd, ["myconf4"], [])

        dd = getDependency(dependencies, "yourmodule10")
        assertNotNull(dd)
        assertEquals("yourorg", dd.getDependencyId().getOrganisation())
        assertEquals("10.1", dd.getDependencyRevisionId().getRevision())
        assertEquals(new HashSet(["*"]), new HashSet(Arrays.asList(dd.getModuleConfigurations())))
        assertDependencyArtifactIncludeRules(dd, ["myconf1"], ["your.*", PatternMatcher.ANY_EXPRESSION])
        assertDependencyArtifactIncludeRules(dd, ["myconf2"], ["your.*", PatternMatcher.ANY_EXPRESSION])
        assertDependencyArtifactIncludeRules(dd, ["myconf3"], ["your.*", PatternMatcher.ANY_EXPRESSION])
        assertDependencyArtifactIncludeRules(dd, ["myconf4"], ["your.*", PatternMatcher.ANY_EXPRESSION])
        assertDependencyArtifactExcludeRules(dd, ["myconf1"], ["toexclude"])
        assertDependencyArtifactExcludeRules(dd, ["myconf2"], ["toexclude"])
        assertDependencyArtifactExcludeRules(dd, ["myconf3"], ["toexclude"])
        assertDependencyArtifactExcludeRules(dd, ["myconf4"], ["toexclude"])
        true
    }

    void assertDependencyArtifactExcludeRules(DependencyDescriptor dd, List<String> confs,
                                              List<String> artifactsNames) {
        ExcludeRule[] rules = dd.getExcludeRules(confs.toArray(new String[confs.size()]))
        assertNotNull(rules)
        assertEquals(artifactsNames.size(), rules.length)
        for (String artifactName : artifactsNames) {
            boolean found = false
            for (int j = 0; j < rules.length; j++) {
                assertNotNull(rules[j])
                if (rules[j].getId().getName().equals(artifactName)) {
                    found = true
                    break
                }
            }
            assertTrue("dependency exclude not found: " + artifactName, found)
        }
    }

    protected void assertDependencyArtifactIncludeRules(DependencyDescriptor dd, List<String> confs,
                                                        List<String> artifactsNames) {
        IncludeRule[] dads = dd.getIncludeRules(confs.toArray(new String[confs.size()]))
        assertNotNull(dads)
        assertEquals(artifactsNames.size(), dads.length)
        for (String artifactName : artifactsNames) {
            boolean found = false
            for (int j = 0; j < dads.length; j++) {
                assertNotNull(dads[j])
                if (dads[j].getId().getName().equals(artifactName)) {
                    found = true
                    break
                }
            }
            assertTrue("dependency include not found: " + artifactName, found)
        }
    }

    protected void assertArtifacts(Artifact[] artifacts, String[] artifactsNames) {
        assertNotNull(artifacts)
        assertEquals(artifactsNames.length, artifacts.length)
        for (int i = 0; i < artifactsNames.length; i++) {
            boolean found = false
            for (int j = 0; j < artifacts.length; j++) {
                assertNotNull(artifacts[j])
                if (artifacts[j].getName().equals(artifactsNames[i])) {
                    found = true
                    break
                }
            }
            assertTrue("artifact not found: " + artifactsNames[i], found)
        }
    }

    protected void assertConf(ModuleDescriptor md, String name, String desc, Configuration.Visibility visibility,
                              String[] exts) {
        Configuration conf = md.getConfiguration(name)
        assertNotNull("configuration not found: " + name, conf)
        assertEquals(name, conf.getName())
        assertEquals(desc, conf.getDescription())
        assertEquals(visibility, conf.getVisibility())
        assertEquals(Arrays.asList(exts), Arrays.asList(conf.getExtends()))
    }

    protected DependencyDescriptor getDependency(DependencyDescriptor[] dependencies, String name) {
        for (int i = 0; i < dependencies.length; i++) {
            assertNotNull(dependencies[i])
            assertNotNull(dependencies[i].getDependencyId())
            if (name.equals(dependencies[i].getDependencyId().getName())) {
                return dependencies[i]
            }
        }
        return null
    }

    protected void assertDependencyArtifacts(DependencyDescriptor dd, List<String> confs,
                                             List<String> artifactsNames) {
        DependencyArtifactDescriptor[] dads = dd.getDependencyArtifacts(confs.toArray(new String[confs.size()]));
        assertNotNull(dads);
        assertEquals(artifactsNames.size(), dads.length);
        for (String artifactName : artifactsNames) {
            boolean found = false;
            for (int j = 0; j < dads.length; j++) {
                assertNotNull(dads[j]);
                if (dads[j].getName().equals(artifactName)) {
                    found = true;
                    break;
                }
            }
            assertTrue("dependency artifact not found: " + artifactName, found);
        }
    }
}