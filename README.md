# Agentic Sales Funnel on Liferay

Demo project for the Liferay DEVCON 2026 talk *"Agentic Sales Funnel on Liferay: Where AI Hub Does
the Thinking and DXP Does the Governing"*.

One focused use case: a visitor submits a form on the customer's site, a classifier agent scores and
segments the lead, an outreach agent writes a first message, and an automation opens the channel.
Nobody clicks anything in between. Every step is bounded by DXP's access controls and recorded in its
audit trail.

## The shape of it

The customer's DXP runs on SaaS with the Client Extension. The AI Hub is a separate service operated
by the provider: it holds the agent definitions, an account per customer (provisioned with an OAuth2
client-credentials application), and that account's token balance.

```
Form submitted on the customer site (Form Container → Lead object, triageStatus = new)
  │
  ├─ Object Action  onAfterAdd, condition triageStatus == "new"
  │    └─▶ Client Extension  POST /object/action/triage-lead
  │          ├─ PATCH the Lead: triageStatus = triaging, answer 202
  │          ├─ {AI Hub}/o/oauth2/token               client credentials of the customer's account
  │          ├─ POST {AI Hub}/o/ai-hub/v1.0/agent-instances   DEVCON_LEAD_CLASSIFIER  (asynchronous: false)
  │          ├─ POST {AI Hub}/o/ai-hub/v1.0/agent-instances   DEVCON_LEAD_OUTREACH    (asynchronous: false)
  │          └─ PATCH the Lead: segment, fitScore, confidence, rationale, message
  │                            triageStatus = qualified (or rejected)
  │
  └─ Object Action  onAfterUpdate, condition triageStatus == "qualified"
       └─▶ Client Extension  POST /object/action/open-channel
             ├─ PUT /o/c/devconchannels/by-external-reference-code/DEVCON_LEAD_CHANNEL_<id>   linked to the Lead
             └─ PATCH the Lead: channelURL, triageStatus = channelOpened
```

A disqualified lead, or one the classifier scores below `com.liferay.devcon.funnel.min.fit.score`
(40), goes straight to `rejected`, with no message and no channel: the model scores, the threshold
is the business's. Two more Object
Actions email the lead: the outreach at `channelOpened`, a thank-you at `rejected`. The hidden page
`/board` of the demo site is the leads board shown on stage.

## Layout

| Path | What it is |
|---|---|
| `agents/` | The two agents: each one's workflow (`workflow-definition.xml` + `.json`) and Agent Definition (`agent-definition.json`) |
| `client-extensions/agentic-funnel-etc-spring-boot/` | The code half: two Object Action endpoints, the AI Hub client, the write-back. |
| `client-extensions/agentic-funnel-site-initializer/` | Everything else, created on deploy: the triage picklists, the Lead object with both Object Actions, and the demo site in the DEVCON '26 look with the lead form. |

## Running it

The demo ran on a Liferay DXP on Liferay Cloud. The host names in the repository are placeholders:
before building, set `.serviceAddress` in
`client-extensions/agentic-funnel-etc-spring-boot/client-extension.yaml` to the Spring Boot
service's host, and `com.liferay.lxc.dxp.domains` / `com.liferay.lxc.dxp.mainDomain` in
`application-default.properties` to your DXP's host.

Build both Client Extensions and deploy the zips from `client-extensions/*/dist/`:

```bash
./gradlew :client-extensions:agentic-funnel-etc-spring-boot:build \
    :client-extensions:agentic-funnel-site-initializer:build
```

Deploy the Spring Boot one first, then `agentic-funnel-site-initializer`, which creates the
picklists, the Lead object with the Object Actions that point at the Spring Boot endpoints, and the
site with its form on top of that object. The brand images (`brand-devcon-liferay.png`,
`brand-devcon-logo.png`, `brand-hero-art.jpg`, `brand-shape-star.jpg`) are not versioned: put your
own in
`client-extensions/agentic-funnel-site-initializer/site-initializer/fragments/group/devcon/resources/`
before building, or the fragments render without them. Locally, `docker compose up -d` and
`./gradlew deploy` do the same against a DXP container.

Publish the two agents to the customer's AI Hub account by ERC, so the ERCs the Client Extension
calls stay fixed: PUT each `workflow-definition.json` (with `workflow-definition.xml` as its content)
to `/o/headless-admin-workflow/v1.0/workflow-definitions/by-external-reference-code/{erc}`, then its
`agent-definition.json` to `/o/ai-hub/agent-definitions/by-external-reference-code/{erc}`.

The Spring Boot Client Extension needs three environment variables, which it refuses to start
without:

| Variable | Value |
|---|---|
| `COM_LIFERAY_DEVCON_FUNNEL_AI_HUB_URL` | Base URL of the AI Hub, e.g. `https://aihub.example.com` |
| `COM_LIFERAY_DEVCON_FUNNEL_AI_HUB_CLIENT_ID` | Client ID of the account's OAuth2 application, from AI Hub provisioning |
| `COM_LIFERAY_DEVCON_FUNNEL_AI_HUB_CLIENT_SECRET` | Its client secret |

On Liferay Cloud, `LCP.json` reads them from the secrets `ai-hub-url`, `ai-hub-client-id` and
`ai-hub-client-secret`. Locally, export the equivalent `COM_LIFERAY_DEVCON_FUNNEL_*` variables before starting it.
