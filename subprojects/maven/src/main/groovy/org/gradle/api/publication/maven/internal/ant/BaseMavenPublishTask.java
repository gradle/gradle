package org.gradle.api.publication.maven.internal.ant;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.beust.jcommander.internal.Lists;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.LocalRepository;
import org.apache.maven.artifact.ant.Proxy;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.artifact.AttachedArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.tools.ant.BuildException;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.embed.Embedder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public abstract class BaseMavenPublishTask implements MavenPublishTaskSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseMavenPublishTask.class);

    private static final String WILDCARD = "*";

    private static final String EXTERNAL_WILDCARD = "external:*";

    private static ClassLoader plexusClassLoader;

    private final File pomFile;

    private Settings settings;

    private PlexusContainer container;

    private LocalRepository localRepository;

    protected File mainArtifact;
    protected List<AdditionalArtifact> additionalArtifacts = Lists.newArrayList();

    protected BaseMavenPublishTask(File pomFile) {
        this.pomFile = pomFile;
    }

    public void setMainArtifact(File file) {
        this.mainArtifact = file;
    }

    @Override
    public void addAdditionalArtifact(File file, String type, String classifier) {
        AdditionalArtifact artifact = new AdditionalArtifact();
        artifact.setFile(file);
        artifact.setType(type);
        artifact.setClassifier(classifier);

        additionalArtifacts.add(artifact);
    }

    protected ArtifactRepository createLocalArtifactRepository() {
        ArtifactRepositoryLayout repositoryLayout =
                (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, getLocalRepository().getLayout());

        return new DefaultArtifactRepository("local", "file://" + getLocalRepository().getPath(), repositoryLayout);
    }

    /**
     * Create a core-Maven ArtifactRepositoryFactory from a Maven Ant Tasks's RemoteRepository definition, eventually configured with authentication and proxy information.
     *
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepositoryFactory
     */
    protected ArtifactRepositoryFactory getArtifactRepositoryFactory(RemoteRepository repository) {
        WagonManager manager = (WagonManager) lookup(WagonManager.ROLE);

        Authentication authentication = repository.getAuthentication();
        if (authentication != null) {
            manager.addAuthenticationInfo(repository.getId(), authentication.getUserName(),
                    authentication.getPassword(), authentication.getPrivateKey(),
                    authentication.getPassphrase());
        }

        Proxy proxy = repository.getProxy();
        if (proxy != null) {
            manager.addProxy(proxy.getType(), proxy.getHost(), proxy.getPort(), proxy.getUserName(),
                    proxy.getPassword(), proxy.getNonProxyHosts());
        }

        return (ArtifactRepositoryFactory) lookup(ArtifactRepositoryFactory.ROLE);
    }

    protected void releaseArtifactRepositoryFactory(ArtifactRepositoryFactory repositoryFactory) {
        try {
            getContainer().release(repositoryFactory);
        } catch (ComponentLifecycleException e) {
            // TODO: Warn the user, or not?
        }
    }

    protected LocalRepository getDefaultLocalRepository() {
        Settings settings = getSettings();
        LocalRepository localRepository = new LocalRepository();
        localRepository.setId("local");
        localRepository.setPath(new File(settings.getLocalRepository()));
        return localRepository;
    }

    protected abstract void doPublish(Artifact artifact, File pomFile, ArtifactRepository localRepo);

    public synchronized Settings getSettings() {
        return settings;
    }

    public void initSettings(File settingsFile) {
        this.settings = new MavenSettingsLoader().loadSettings(settingsFile);

        WagonManager wagonManager = (WagonManager) lookup(WagonManager.ROLE);
        wagonManager.setDownloadMonitor(new LoggingTransferListener());
    }

    protected Object lookup(String role) {
        try {
            return getContainer().lookup(role);
        } catch (ComponentLookupException e) {
            throw new BuildException("Unable to find component: " + role, e);
        }
    }

    protected Object lookup(String role,
                            String roleHint) {
        try {
            return getContainer().lookup(role, roleHint);
        } catch (ComponentLookupException e) {
            throw new BuildException("Unable to find component: " + role + "[" + roleHint + "]", e);
        }
    }

    @Override
    public synchronized PlexusContainer getContainer() {
        if (container == null) {
            try {
                ClassWorld classWorld = new ClassWorld();

                classWorld.newRealm("plexus.core", getClass().getClassLoader());

                Embedder embedder = new Embedder();

                embedder.start(classWorld);

                container = embedder.getContainer();
            } catch (PlexusContainerException e) {
                throw new BuildException("Unable to start embedder", e);
            } catch (DuplicateRealmException e) {
                throw new BuildException("Unable to create embedder ClassRealm", e);
            }
        }

        return container;
    }

    public LocalRepository getLocalRepository() {
        if (localRepository == null) {
            localRepository = getDefaultLocalRepository();
        }
        return localRepository;
    }

    /**
     * @noinspection RefusedBequest
     */
    public void execute() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            if (plexusClassLoader != null) {
                Thread.currentThread().setContextClassLoader(plexusClassLoader);
            }
            doExecute();
        } finally {
            plexusClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public void doExecute() {
        if (mainArtifact == null && (additionalArtifacts.size() == 0)) {
            throw new BuildException("You must specify a file and/or an attached artifact for Maven publishing.");
        }

        ArtifactRepository localRepo = createLocalArtifactRepository();
        ParsedMavenPom parsedMavenPom = new ParsedMavenPom(pomFile);

        Artifact artifact = createMainArtifact(parsedMavenPom);
        if (mainArtifact != null) {
            boolean isPomArtifact = "pom".equals(parsedMavenPom.getPackaging());
            if (isPomArtifact) {
                doPublish(artifact, pomFile, localRepo);
            } else {
                ArtifactMetadata metadata = new ProjectArtifactMetadata(artifact, pomFile);
                artifact.addMetadata(metadata);
                doPublish(artifact, mainArtifact, localRepo);
            }
        }

        for (AdditionalArtifact attachedArtifact : additionalArtifacts) {
            Artifact attach = createAttachedArtifact(artifact, attachedArtifact.getType(), attachedArtifact.getClassifier());
            doPublish(attach, attachedArtifact.getFile(), localRepo);
        }
    }
    public Artifact createMainArtifact(ParsedMavenPom builder)
    {
        return new DefaultArtifact(builder.getGroup(), builder.getArtifactId(), VersionRange.createFromVersion(builder.getVersion()),
                null, builder.getPackaging(), null, artifactHandler(builder.getPackaging()));
    }

    public Artifact createAttachedArtifact(Artifact mainArtifact, String type, String classifier) {
        return new AttachedArtifact(mainArtifact, type, classifier, artifactHandler(type));
    }

    private DefaultArtifactHandler artifactHandler(String type) {
        return new DefaultArtifactHandler(type);
    }

    /**
     * This method checks if the pattern matches the originalRepository. Valid patterns: * = everything external:* = everything not on the localhost and not file based. repo,repo1 = repo or repo1
     * *,!repo1 = everything except repo1
     *
     * @param originalRepository to compare for a match.
     * @param pattern used for match. Currently only '*' is supported.
     * @return true if the repository is a match to this pattern.
     */
    boolean matchPattern(RemoteRepository originalRepository, String pattern) {
        boolean result = false;
        String originalId = originalRepository.getId();

        // simple checks first to short circuit processing below.
        if (WILDCARD.equals(pattern) || pattern.equals(originalId)) {
            result = true;
        } else {
            // process the list
            String[] repos = pattern.split(",");

            for (String repo : repos) {
                // see if this is a negative match
                if (repo.length() > 1 && repo.startsWith("!")) {
                    if (originalId.equals(repo.substring(1))) {
                        // explicitly exclude. Set result and stop processing.
                        result = false;
                        break;
                    }
                }
                // check for exact match
                else if (originalId.equals(repo)) {
                    result = true;
                    break;
                }
                // check for external:*
                else if (EXTERNAL_WILDCARD.equals(repo) && isExternalRepo(originalRepository)) {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                } else if (WILDCARD.equals(repo)) {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
            }
        }
        return result;
    }

    /**
     * Checks the URL to see if this repository refers to an external repository
     *
     * @return true if external.
     */
    boolean isExternalRepo(RemoteRepository originalRepository) {
        try {
            URL url = new URL(originalRepository.getUrl());
            return !(url.getHost().equals("localhost") || url.getHost().equals("127.0.0.1") || url.getProtocol().equals("file"));
        } catch (MalformedURLException e) {
            // bad url just skip it here. It should have been validated already, but the wagon lookup will deal with it
            return false;
        }
    }

    protected void log(String message) {
        LOGGER.info(message);
    }

    protected void log(String message, int level) {
        LOGGER.info(message);
    }

    private class LoggingTransferListener implements TransferListener {
        private static final int KILO = 1024;

        protected void log(String message) {
            LOGGER.info(message);
        }

        public void debug(String s) {
            LOGGER.debug(s);
        }

        public void transferError(TransferEvent event) {
            LOGGER.error(event.getException().getMessage());
        }

        public void transferInitiated(TransferEvent event) {
            String message = event.getRequestType() == TransferEvent.REQUEST_PUT ? "Uploading" : "Downloading";
            String dest = event.getRequestType() == TransferEvent.REQUEST_PUT ? " to " : " from ";

            LOGGER.info(message + ": " + event.getResource().getName() + dest + "repository "
                    + event.getWagon().getRepository().getId() + " at " + event.getWagon().getRepository().getUrl());
        }

        public void transferStarted(TransferEvent event) {
            long contentLength = event.getResource().getContentLength();
            if (contentLength > 0) {
                LOGGER.info("Transferring " + ((contentLength + KILO / 2) / KILO) + "K from "
                        + event.getWagon().getRepository().getId());
            }
        }

        public void transferProgress(TransferEvent event, byte[] bytes, int i) {
        }

        public void transferCompleted(TransferEvent event) {
            long contentLength = event.getResource().getContentLength();
            if ((contentLength > 0) && (event.getRequestType() == TransferEvent.REQUEST_PUT)) {
                LOGGER.info("Uploaded " + ((contentLength + KILO / 2) / KILO) + "K");
            }
        }
    }

    private static class AdditionalArtifact {
        File file;
        String type;
        String classifier;

        public void setFile(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setClassifier(String classifier) {
            this.classifier = classifier;
        }

        public String getClassifier() {
            return classifier;
        }
    }

}
