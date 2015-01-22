/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.*;
import org.apache.maven.project.injection.ModelDefaultsInjector;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.tools.ant.BuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;


/**
 * A POM typedef. Also an Ant Task that registers a handler called POMPropertyHelper that intercepts all calls to
 * property value resolution and replies instead of Ant to properties that start with the id of the pom. Example:
 * ${maven.project.artifactId}
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:nicolaken@apache.org">Nicola Ken Barozzi</a>
 * @version $Id: Pom.java 1085345 2011-03-25 12:13:31Z stephenc $
 */
public class MavenArtifactBuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenArtifactBuilder.class);

    private final MavenProjectBuilder mavenProjectBuilder;
    private final ModelDefaultsInjector modelDefaultsInjector;
    private final MavenProjectHelper helper;

    private ProfileManager profileManager;

    /**
     * The maven project represented by this pom
     */
    private MavenProject mavenProject;

    /**
     * The file from which the pom was loaded.
     */
    private File pomFile;

    public MavenArtifactBuilder(MavenProjectBuilder mavenProjectBuilder, ModelDefaultsInjector modelDefaultsInjector, MavenProjectHelper helper, ProfileManager profileManager) {
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.modelDefaultsInjector = modelDefaultsInjector;
        this.helper = helper;
        this.profileManager = profileManager;
    }

    public void setPomFile(File pomFile)
    {
        this.pomFile = pomFile;
    }

    public void attach( AttachedArtifact attached )
    {
        MavenProject project = mavenProject;
        if ( attached.getClassifier() != null )
        {
            helper.attachArtifact( project, attached.getType(), attached.getClassifier(), attached.getFile() );
        }
        else
        {
            helper.attachArtifact( project, attached.getType(), attached.getFile() );
        }
    }

    public File getPomFile()
    {
        return pomFile;
    }

    public Artifact getMainArtifact()
    {
        return mavenProject.getArtifact();
    }

    public List<Artifact> getAttachedArtifacts()
    {
        return mavenProject.getAttachedArtifacts();
    }

    public void initialiseMavenProject(ArtifactRepository localRepository )
    {
        ProjectBuilderConfiguration builderConfig = this.createProjectBuilderConfig( localRepository );
        try
        {
            mavenProject = mavenProjectBuilder.build(pomFile, builderConfig );

            mavenProjectBuilder.calculateConcreteState( mavenProject, builderConfig, false );
        }
        catch ( ProjectBuildingException pbe )
        {
            throw new BuildException( "Unable to initialize POM " + pomFile.getName() + ": " + pbe.getMessage(), pbe );
        }
        catch ( ModelInterpolationException mie )
        {
            throw new BuildException( "Unable to interpolate POM " + pomFile.getName() + ": " + mie.getMessage(), mie );
        }

        modelDefaultsInjector.injectDefaults(mavenProject.getModel());
    }

    public boolean isPomPackaging() {
        return "pom".equals(mavenProject.getPackaging());
    }

    /**
     * Create a project builder configuration to be used when initializing the maven project.
     *
     * @return
     */
    private ProjectBuilderConfiguration createProjectBuilderConfig( ArtifactRepository localArtifactRepository )
    {
        ProjectBuilderConfiguration builderConfig = new DefaultProjectBuilderConfiguration();
        builderConfig.setLocalRepository( localArtifactRepository );
        builderConfig.setGlobalProfileManager(profileManager);
//        builderConfig.setUserProperties( getAntProjectProperties() );
//        builderConfig.setExecutionProperties( getAntProjectProperties() );

        return builderConfig;
    }

    protected void log(String message) {
        LOGGER.info(message);
    }

}
