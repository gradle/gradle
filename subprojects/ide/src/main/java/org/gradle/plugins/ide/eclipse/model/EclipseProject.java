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
package org.gradle.plugins.ide.eclipse.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.util.ConfigureUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enables fine-tuning project details (.project file) of the Eclipse plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure eclipse project directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * eclipse {
 *   project {
 *     //if you don't like the name Gradle has chosen
 *     name = 'someBetterName'
 *
 *     //if you want to specify the Eclipse project's comment
 *     comment = 'Very interesting top secret project'
 *
 *     //if you want to append some extra referenced projects in a declarative fashion:
 *     referencedProjects 'someProject', 'someOtherProject'
 *     //if you want to assign referenced projects
 *     referencedProjects = ['someProject'] as Set
 *
 *     //if you want to append some extra natures in a declarative fashion:
 *     natures 'some.extra.eclipse.nature', 'some.another.interesting.nature'
 *     //if you want to assign natures in a groovy fashion:
 *     natures = ['some.extra.eclipse.nature', 'some.another.interesting.nature']
 *
 *     //if you want to append some extra build command:
 *     buildCommand 'buildThisLovelyProject'
 *     //if you want to append a build command with parameters:
 *     buildCommand 'buildItWithTheArguments', argumentOne: "I'm first", argumentTwo: "I'm second"
 *
 *     //if you want to create an extra link in the eclipse project,
 *     //by location uri:
 *     linkedResource name: 'someLinkByLocationUri', type: 'someLinkType', locationUri: 'file://someUri'
 *     //by location:
 *     linkedResource name: 'someLinkByLocation', type: 'someLinkType', location: '/some/location'
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases users can perform advanced configuration on resulting XML file.
 * It is also possible to affect the way eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * beforeMerged and whenMerged closures receive {@link Project} object
 * <p>
 * Examples of advanced configuration:
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * eclipse {
 *   project {
 *
 *     file {
 *       //if you want to mess with the resulting XML in whatever way you fancy
 *       withXml {
 *         def node = it.asNode()
 *         node.appendNode('xml', 'is what I love')
 *       }
 *
 *       //closure executed after .project content is loaded from existing file
 *       //but before gradle build information is merged
 *       beforeMerged { project ->
 *         //if you want skip merging natures... (a very abstract example)
 *         project.natures.clear()
 *       }
 *
 *       //closure executed after .project content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { project ->
 *         //you can tinker with the {@link Project} here
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class EclipseProject {

    public static final ImmutableSet<String> VALID_LINKED_RESOURCE_ARGS = ImmutableSet.of("name", "type", "location", "locationUri");
    private String name;

    private String comment;

    private Set<String> referencedProjects = Sets.newLinkedHashSet();

    private List<String> natures = Lists.newArrayList();

    private List<BuildCommand> buildCommands = Lists.newArrayList();

    private Set<Link> linkedResources = Sets.newLinkedHashSet();

    private final XmlFileContentMerger file;

    public EclipseProject(XmlFileContentMerger file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    /**
     * Configures eclipse project name. It is <b>optional</b> because the task should configure it correctly for you.
     * By default it will try to use the <b>project.name</b> or prefix it with a part of a <b>project.path</b>
     * to make sure the moduleName is unique in the scope of a multi-module build.
     * The 'uniqueness' of a module name is required for correct import
     * into Eclipse and the task will make sure the name is unique.
     * <p>
     * The logic that makes sure project names are unique is available <b>since</b> 1.0-milestone-2
     * <p>
     * If your project has problems with unique names it is recommended to always run gradle eclipse from the root, e.g. for all subprojects, including generation of .classpath.
     * If you run the generation of the eclipse project only for a single subproject then you may have different results
     * because the unique names are calculated based on eclipse projects that are involved in the specific build run.
     * <p>
     * If you update the project names then make sure you run gradle eclipse from the root, e.g. for all subprojects.
     * The reason is that there may be subprojects that depend on the subproject with amended eclipse project name.
     * So you want them to be generated as well because the project dependencies in .classpath need to refer to the amended project name.
     * Basically, for non-trivial projects it is recommended to always run gradle eclipse from the root.
     * <p>
     * For example see docs for {@link EclipseProject}
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    /**
     * A comment used for the eclipse project. By default it will be configured to <b>project.description</b>
     * <p>
     * For example see docs for {@link EclipseProject}
     */
    public void setComment(String comment) {
        this.comment = comment;
    }


    public Set<String> getReferencedProjects() {
        return referencedProjects;
    }

    /**
     * The referenced projects of this Eclipse project (*not*: java build path project references).
     * <p>
     * Referencing projects does not mean adding a build path dependencies between them!
     * If you need to configure a build path dependency use Gradle's dependencies section or
     * eclipse.classpath.whenMerged { classpath -> ... to manipulate the classpath entries
     * <p>
     * For example see docs for {@link EclipseProject}
     */
    public void setReferencedProjects(Set<String> referencedProjects) {
        this.referencedProjects = referencedProjects;
    }

    /**
     * The referenced projects of this Eclipse project (*not*: java build path project references). <p> Referencing projects does not mean adding a build path dependencies between them! If you need to
     * configure a build path dependency use Gradle's dependencies section or eclipse.classpath.whenMerged { classpath -> ... to manipulate the classpath entries
     *
     * @param referencedProjects The name of the project references.
     */
    public void referencedProjects(String... referencedProjects) {
        assert referencedProjects != null;
        this.referencedProjects.addAll(Arrays.asList(referencedProjects));
    }

    public List<String> getNatures() {
        return natures;
    }
    /**
     * The natures to be added to this Eclipse project.
     * <p>
     * For example see docs for {@link EclipseProject}
     */
    public void setNatures(List<String> natures) {
        this.natures = natures;
    }

    /**
     * Appends natures entries to the eclipse project. <p> For example see docs for {@link EclipseProject}
     *
     * @param natures the nature names
     */
    public void natures(String... natures) {
        assert natures != null;
        this.natures.addAll(Arrays.asList(natures));
    }

    public List<BuildCommand> getBuildCommands() {
        return buildCommands;
    }

    /**
     * The build commands to be added to this Eclipse project.
     * <p>
     * For example see docs for {@link EclipseProject}
     */
    public void setBuildCommands(List<BuildCommand> buildCommands) {
        this.buildCommands = buildCommands;
    }

    /**
     * Adds a build command with arguments to the eclipse project. <p> For example see docs for {@link EclipseProject}
     *
     * @param args A map with arguments, where the key is the name of the argument and the value the value.
     * @param buildCommand The name of the build command.
     * @see #buildCommand(String)
     */
    public void buildCommand(Map<String, String> args, String buildCommand) {
        assert buildCommand != null;
        buildCommands.add(new BuildCommand(buildCommand, args));
    }

    /**
     * Adds a build command to the eclipse project. <p> For example see docs for {@link EclipseProject}
     *
     * @param buildCommand The name of the build command
     * @see #buildCommand(Map, String)
     */
    public void buildCommand(String buildCommand) {
        assert buildCommand != null;
        buildCommands.add(new BuildCommand(buildCommand));
    }

    public Set<Link> getLinkedResources() {
        return linkedResources;
    }

    /**
     * The linked resources to be added to this Eclipse project.
     * <p>
     * For example see docs for {@link EclipseProject}
     */
    public void setLinkedResources(Set<Link> linkedResources) {
        this.linkedResources = linkedResources;
    }

    /**
     * Adds a resource link (aka 'source link') to the eclipse project. <p> For example see docs for {@link EclipseProject}
     *
     * @param args A maps with the args for the link. Legal keys for the map are name, type, location and locationUri.
     */
    public void linkedResource(Map<String, String> args) {
        Set<String> illegalArgs = Sets.difference(args.keySet(), VALID_LINKED_RESOURCE_ARGS);
        if (!illegalArgs.isEmpty()) {
            throw new InvalidUserDataException("You provided illegal argument for a link: " + illegalArgs + ". Valid link args are: " + VALID_LINKED_RESOURCE_ARGS);
        }

        linkedResources.add(new Link(args.get("name"), args.get("type"), args.get("location"), args.get("locationUri")));
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way existing .project content is merged with gradle build information <p> The object passed to whenMerged{}
     * and beforeMerged{} closures is of type {@link Project} <p>
     *
     * For example see docs for {@link EclipseProject}
     */
    public void file(Closure closure) {
        ConfigureUtil.configure(closure, file);
    }

    /**
     * See {@link #file(Closure)}
     */
    public final XmlFileContentMerger getFile() {
        return file;
    }

    @SuppressWarnings("unchecked")
    public void mergeXmlProject(Project xmlProject) {
        Project decoratedProject = new NonRenamableProject(xmlProject);
        file.getBeforeMerged().execute(decoratedProject);
        xmlProject.configure(this);
        file.getWhenMerged().execute(decoratedProject);
    }
}
