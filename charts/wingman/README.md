# Wingman Helm package

Opt-in packaging only. Existing `deploy/`, root Dockerfile, GitHub Action and
`argocd/application.yaml` remain the active Node/Kustomize deployment path.
Defaults reproduce its image, replicas, resource budget, selectors, Service `app`
port 80, `/healthz` liveness and `/readyz` readiness. The inert HTTPRoute is retained.
Names are deliberately fixed: this is one app per namespace, not multiple releases.

Local validation requires Helm 3 or 4 and Ruby with its standard YAML library:

```sh
ruby scripts/validate-helm.rb
helm template app charts/wingman --namespace team-wingman
helm package charts/wingman --destination /tmp
```

The validator checks both runtime configurations, rejects incomplete backend
configuration, and renders a packaged archive in a temporary directory. It does
not contact Kubernetes or validate server-side APIs.

## Backend image and configuration

The root Dockerfile builds only the Node starter. Build the backend separately:

```sh
docker build -f backend/Dockerfile --build-arg APP_VERSION=review -t wingman-backend:review .
helm template app charts/wingman --namespace team-wingman -f charts/wingman/values-backend.yaml --set image.repository=wingman-backend --set-string image.tag=review --set existingSecret=backend-config
```

This local tag is for build/render review only; cluster use requires an actual
published public image. The chart creates no Secrets. Privately provision the named
Secret in the destination namespace with `DATABASE_URL` (a JDBC PostgreSQL URL),
`DATABASE_USERNAME`, and `DATABASE_PASSWORD`, plus any provider configuration needed
by the backend version being packaged. Do not copy local `.env` contents into git
or image layers. No database, Elasticsearch, AI provider, telemetry service or
frontend is installed by this chart.

Backend pods listen on all interfaces at 8080 and report the image tag at
`/api/v1/version`. Readiness retains the database check; a startup probe allows up
to three minutes before normal liveness probing. Backend values use one replica
and a 512Mi memory limit; verify total cluster headroom before any future rollout.
This is backend API packaging, not a unified frontend deployment or a replacement
for the starter `/api/sessions` API.

## Future GitOps adoption

Do not install a Helm release over Argo-owned resources. Adoption requires a
separate reviewed change to the existing Application source path
(`charts/wingman`) and Helm values, together with CI image updates to this chart
instead of `deploy/kustomization.yaml`. Keep the same Application and namespace
ownership and preserve the HTTPRoute during migration. Backend adoption also needs
its image build/publish job and private database configuration. None of those
deployment changes are activated here.
