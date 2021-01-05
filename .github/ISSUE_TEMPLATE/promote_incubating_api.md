---
name: 'Promote Incubating API'
about: 'Checklist for de-incubating an API in Gradle 7.0'
labels: 'a:chore, from:member'
---

### Summary
<!--- List the API methods for de-incubation -->

### Checklist
- [ ] Validate whether we should de-incubate the API in the 7.0 release.
    - If the removal is not possible, create a follow-up issue and link it in the [overview spreadsheet](https://docs.google.com/spreadsheets/d/19J1nR_dFKpfKdu5KDFMVZGfjR0ysT9DthsBUPwf8mkM/edit#gid=1195622786)
- [ ] Update release notes (add API to promoted features)
- [ ] Check User Manual (it might mention that the API is still incubating)
  - Think about updating snippets and samples to use this API
- [ ] Deprecate existing API that is replaced by the new one
  - If it's not immediately possible, create a follow-up issue with a milestone (e.g. `7.1 RC1` or `8.0 RC1`); list for 7.1: #15681
- [ ] Check Incubation Report on TeamCity ([example](https://builds.gradle.org/viewLog.html?buildId=40024670&buildTypeId=Gradle_Check_SanityCheck&tab=report_project951_Incubating_APIs_Report)) 
- [ ] Mark the story as done in the [overview spreadsheet](https://docs.google.com/spreadsheets/d/19J1nR_dFKpfKdu5KDFMVZGfjR0ysT9DthsBUPwf8mkM/edit?ts=5fcfefb8#gid=0).  
