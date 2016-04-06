A set of candidates for making the resolution of remote dependencies faster, and in particular improving build speed perception in the presence of changing dependencies (such as snapshots, dependency ranges, changing resources, etc).

## Candidate changes

- Provide a way to declare the likelihood of a change on a per module level. For example, a way to say check for changes to this module more often, and this module occasionally.
- Check for changes as a batch. For example, check for changes at a given time each day, rather than after a particular period has expired since the particlar module was checked.
- Parallel downloads and remote up-to-date checks.
- Daemon performs preemptive remote up-to-date checks and downloads.
- Make network access visible as progress logging events.
