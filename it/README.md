# HTTP integration tests

## Running

```sh
sbt check
sbt 'it/test'
sbt 'it/testOnly controllers.VulnerabilityPageISpec'
```

`check` runs `test` followed by `it/test`. Use it as the combined local/CI check; `sbt test` alone does not run this separate subproject. No Service Manager services, MongoDB or backend credentials are needed. Play and WireMock manage their own test ports and shut down after the tests.

## Boundary under test

`VulnerabilityPageISpec` sends real HTTP requests to Play. Requests pass through production routing, session-cookie filters, Internal Auth, controllers, services, connectors, JSON readers and Twirl rendering. The suite checks the response status, useful HTML content, navigation URLs and relevant upstream requests. It also fetches a shared Catalogue stylesheet to check asset routing.

`IntegrationSpec` normally configures both `vulnerabilities.use-stub` and `releases-api.use-stub` to `false`. WireMock stands in for vulnerabilities, releases-api, Internal Auth and Catalogue Config. Do not replace the auth action or application services with unit-test fakes here. Signed-in requests use the application's signed and encrypted session cookies, and authorisation responses come from the HTTP stub.

Each test gets a fresh application and cache. WireMock mappings and its request journal reset between tests. Requests within one test share an application, allowing cache behaviour across users and filters to be verified. Tests run sequentially because the WireMock DSL uses shared configuration.

## Adding coverage

- Describe an observable behaviour in the test name. Arrange the upstream responses, make the HTTP request, then assert the result and important calls or non-calls.
- Keep wire-format JSON in `test/resources/fixtures`. Do not generate it with the production model's JSON writer: that can hide a shared reader/writer mistake. These fixtures represent the proposed contracts; they do not prove compatibility with a deployed backend.
- Use distinctive fixture values. This suite uses `CVE-2099-1234`, not the application's Log4j sample, so accidental use of sample data is detectable. The occurrence fixture splits one service across pages and includes multiple teams and app versions; it must still render one assessment per service.
- Assert meaningful DOM content and URLs with Jsoup rather than whole-page snapshots, whitespace or incidental Bootstrap classes. Verify query parameters and call counts where they establish pagination, caching or access-control behaviour.
- Cover failures as well as success. Required advisory/occurrence failures must not show sample or partial data. Missing enrichment must remain unavailable. A releases outage must preserve vulnerability data, mark deployment context unavailable, and allow a later retry.
- Keep timing deterministic. Check caching through upstream request counts; do not sleep until the cache expires. Detailed expiry and concurrent-fetch cases belong in the connector tests, which control time and completion explicitly.
- Use the unit/component suite under `test/` for mapping rules, parameter combinations and individual connector behaviour. Add an integration test when it establishes behaviour across application boundaries rather than repeating every unit case.

The suite currently covers successful rendering and grouping, shared deployment caching, missing enrichment, anonymous and forbidden access, advisory 404/500 responses, malformed later occurrence pages, deployment failure/recovery and versions not reported as running. Expected error-path tests can produce application WARN/ERROR logs; the test summary determines success.

These tests inspect server-rendered HTML. They do not run browser JavaScript or verify visual layout and accessibility in a browser.

`GuidanceISpec` additionally exercises editor permissions, author retrieval, real CSRF-protected forms, PUT bodies (including null deletion), validation and backend errors. `StubGuidanceISpec` deliberately enables only the vulnerability stub to check the local add/edit/delete journey across requests; it still uses real Internal Auth HTTP stubs and production CSRF filters. It verifies audit comments are not published as guidance.
