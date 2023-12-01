# ADR-0001 - Use Architectural Decision Records

## Date

2023-12-01

## Context

In a distributed team with many subteams, the best solution to communicate decisions is to use a format accessible by everyone in charge of development.

We use *Specification* and *Discovery* documents stored in Google Drive, but they present some downsides:

* They are not updated after edition and then are hard to follow, especially after decision-making
* They are not synced with the code
* Google Docs is not a "code oriented" tool, like asciidoc can be
* Review in Google Doc is not as simple as a PR code review in GitHub

## Decision

The *Build Tool Team* has decided to use Architectural Decision Records (aka ADR) to track decisions we want to follow.

The main logic with ADRs is to describe (architectural) decisions made:

* To provide best practices and solutions we (as *build tool* team) want to promote.
* To avoid asking the same thing multiple times during code review.
* To explain *rejected solutions*, for now and future development, in case of re-visit.

ADRs can be written by any team, like code, it has to be reviewed by any other relevant teams.
The goal is not to *own*, but to *share* with other teams, to improve the build tool conjointly.


### Format

The format for ADR in this module should follow this template:

```markdown
# ADR-000X - Title

## Date

20XY-AB-CD

## Context

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna
aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint
occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

## Decision

Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna
aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint
occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

## Status

[PROPOSED, ACCEPTED, REJECTED, DEPRECATED, REPLACED]

## Consequences

* X
* Y
* Z
```

## Status

PROPOSED

## Consequences

* We start to use Architectural Decision Records
* We use the proposed template from this ADR
* We locate `.md` files in the folder `/architecture-standards`
* We highly encourage usage of ADR to communicate decisions
* We use links to ADRs in *Specifications*, *Discoveries* and *Pull-Requests* to simplify communication
