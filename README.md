# rf-query

Testing out some ideas.

## Notes

- Should support some of the same features as TanStack Query
- Should work better with Cljs datastructures
- Response data should be usable in Re-frame event/effect handlers?
  - Is it feasible? How to check if the query status is OK?
- What is the lib API - functions or re-frame?

## What does it do

- `use-query` hook to trigger query fn (which should return a promise) and the result
is stored to re-frame app db
- Queries can be marked invalidated, if they are used by someone, the using component
will trigger refetch
- If the query isn't used by any component, it is cleaned from the app-db after `:gc-time` timeout
