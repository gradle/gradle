package org.gradle.api.publication.maven

def publisher = new DefaultMavenPublisher()
def repo = new DefaultMavenRepository(url: "file:///swd/tmp/m2remoteRepo")
def publication = new DefaultMavenPublication(groupId: "mygroup", artifactId: "myartifact", version: "1.1")
def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: new File("/swd/tmp/fatjar/fatjar.jar"))
publication.mainArtifact = artifact

publisher.install(publication)
publisher.deploy(publication, repo)


