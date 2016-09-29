# Proposal for multi project support in delta

delta itself is a multi project build. Currently, we do not have a
good way to deploy both the delta api and the delta www
application. Currently, when we add 'delta' as a project, you will see
errors in the log that there is no application named 'delta' in the
registry. This occurs because the applications in the registry are
named 'delta-api' and 'delta-www'.

we also have discussed potentially two docker builds for our
postgresql repositories - one for the developer version of the DB, and
one containing a docker image to upgade schema in production.

## Proposed solution

We introduce the notion of a 'build' which has a name (e.g. api) and a
path to a dockerfile (e.g. api/Dockerfile). Each project will have n
builds. Images will be associated to builds. Scaling will operate on a
build.

In the data model:

    create table builds (
      id
      project_id
      name
      dockerfile_path
    )

    table images, project_desired_states, project_last_states:
    
      replace project_id with build_id

In the UI - viewing a project will enumerate the last/desired states
for each of the builds.

Over time we can add features that determine if builds are deployed
sequentially or in parallel (e.g. a new tag on the delta repo itself
might require the 'api' build to be released (including scale down of
old versions) prior to the release of the 'www' build).





