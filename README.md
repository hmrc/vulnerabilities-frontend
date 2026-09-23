
# vulnerabilities-frontend

Design implementation for [BDOG-4000](https://jira.tools.tax.service.gov.uk/browse/BDOG-4000).

The vulnerability details page uses `catalogue-wrapper-play-30` for its layout, menus, search and Bootstrap assets, following operational-metrics-frontend. `VulnerabilityService` calls `VulnerabilitiesConnector`, which returns local case-class fixtures by default. No vulnerability backend is needed to run the page.

The frontend root redirects to the sample vulnerability. Unknown advisory identifiers return 404. Team, service, digital-service, state, priority and SLA-status filters narrow a single occurrence table with one row per service, sorted by service name. Each row links its service and owning teams and shows dependency details directly in the table. Applying or clearing filters returns to the occurrences section. Pages work without JavaScript.

Local dependencies:

- Internal Auth on port 8470, with `vulnerabilities-frontend` / `*` / `READ` permission. Guidance editing additionally requires `vulnerabilities` / `guidance` / `WRITE`.
- Internal Auth Frontend on port 8471 for local test sign-in. Start both with `sm2 --start INTERNAL_AUTH INTERNAL_AUTH_FRONTEND`; visiting the page redirects to the sign-in form, where you can add the permission above.
- Catalogue Config on port 9067 for shared navigation and quick search. The wrapper falls back to cached/empty navigation if unavailable.
- Teams and Repositories on port 9015 for the complete filter directory (also used when vulnerability data is stubbed).
- Releases API on port 8008 when `releases-api.use-stub = false`; deployment fixtures are used by default.
- Catalogue frontend on port 9017 for service and version-specific dependency links (`catalogue-frontend.base-url` is configurable).

This frontend has no Mongo dependency or Government Gateway journey. Internal Auth manages authentication; browser sign-out clears the frontend session. The signed-out and error pages use the same Catalogue wrapper as the vulnerability pages.


### Testing

Run `sbt check` before opening a pull request. This runs both the unit/component suite (`sbt test`) and the HTTP integration suite (`sbt 'it/test'`). No locally running services or database are required.

The integration suite starts the real Play application with both connector stub switches disabled and uses WireMock for upstream HTTP services. It covers authentication and authorisation, paginated occurrence grouping, rendered data and links, deployment caching across users, missing enrichment and upstream failures. See [the integration testing guide](it/README.md) for the conventions to follow when adding tests.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

