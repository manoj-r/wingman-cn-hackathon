#!/usr/bin/env ruby
require 'yaml'
require 'open3'
require 'tmpdir'

ROOT = File.expand_path('..', __dir__)
CHART = File.join(ROOT, 'charts/wingman')
def run(*args)
  output, error, status = Open3.capture3(*args)
  abort "#{args.join(' ')} failed:\n#{output}#{error}" unless status.success?
  output
end
def check(condition, message)
  abort message unless condition
end
def verify(chart, *args)
  docs = YAML.load_stream(run('helm', 'template', 'review', chart, '--namespace', 'team-wingman', *args)).compact
  check(docs.map { |d| d['kind'] }.sort == %w[Deployment HTTPRoute Service], 'Unexpected resources')
  deployment = docs.find { |d| d['kind'] == 'Deployment' }
  service = docs.find { |d| d['kind'] == 'Service' }
  route = docs.find { |d| d['kind'] == 'HTTPRoute' }
  pod = deployment.fetch('spec').fetch('template')
  check(service['metadata']['name'] == 'app', 'Platform service must remain app')
  check(service['spec']['ports'] == [{'name'=>'http', 'port'=>80, 'targetPort'=>'http'}], 'Service port contract changed')
  check(service['spec']['selector'] == deployment['spec']['selector']['matchLabels'], 'Selectors differ')
  check(service['spec']['selector'].all? { |k,v| pod['metadata']['labels'][k] == v }, 'Service cannot select pods')
  check(route['spec']['rules'][0]['backendRefs'] == [{'name'=>'app', 'port'=>80}], 'Route backend changed')
  container = pod['spec']['containers'].fetch(0)
  check(container['readinessProbe']['httpGet'] == {'path'=>'/readyz', 'port'=>'http'}, 'Readiness contract changed')
  check(container['livenessProbe']['httpGet'] == {'path'=>'/healthz', 'port'=>'http'}, 'Liveness contract changed')
  check(container['securityContext']['runAsNonRoot'] && !container['securityContext']['allowPrivilegeEscalation'], 'Unsafe container')
  check(container['resources'].keys.sort == %w[limits requests], 'Missing resource budget')
  container
end

run('helm', 'lint', '--strict', CHART)
starter = verify(CHART)
starter_docs = YAML.load_stream(run('helm', 'template', 'review', CHART, '--namespace', 'team-wingman')).compact
%w[deployment service httproute].each do |name|
  expected = YAML.load_file(File.join(ROOT, "deploy/#{name}.yaml"))
  actual = starter_docs.find { |doc| doc['kind'] == expected['kind'] }
  if name == 'deployment'
    image = YAML.load_file(File.join(ROOT, 'deploy/kustomization.yaml')).fetch('images').find { |entry| entry['name'] == 'app' }
    container = expected['spec']['template']['spec']['containers'].fetch(0)
    container['image'] = "#{image.fetch('newName')}:#{image.fetch('newTag')}"
    container['imagePullPolicy'] = 'IfNotPresent'
    container['env'] += [{'name'=>'PORT', 'value'=>'8080'}, {'name'=>'APP_VERSION', 'value'=>image.fetch('newTag').to_s}]
  end
  check(actual == expected, "Default #{name} differs from deployment contract")
end
backend_args = ['-f', File.join(CHART, 'values-backend.yaml'), '--set', 'image.repository=example.invalid/wingman-backend', '--set-string', 'image.tag=test', '--set', 'existingSecret=backend-config']
run('helm', 'lint', '--strict', CHART, *backend_args)
backend = verify(CHART, *backend_args)
check(backend['image'] == 'example.invalid/wingman-backend:test', 'Image overrides ignored')
check(backend['envFrom'] == [{'secretRef'=>{'name'=>'backend-config'}}], 'Secret reference missing')
env = backend['env'].to_h { |e| [e['name'], e['value']] }
check(env['BACKEND_ADDRESS'] == '0.0.0.0' && env['PORT'] == '8080' && env['APP_VERSION'] == 'test', 'Backend runtime contract changed')
check(backend['startupProbe']['httpGet']['path'] == '/healthz', 'Backend startup probe missing')
_, _, status = Open3.capture3('helm', 'template', 'review', CHART, '-f', File.join(CHART, 'values-backend.yaml'))
check(!status.success?, 'Incomplete backend configuration must fail')
%w[image.repository image.tag existingSecret].each do |key|
  _, _, status = Open3.capture3('helm', 'template', 'review', CHART, *backend_args, '--set-string', "#{key}=")
  check(!status.success?, "Missing #{key} must fail")
end
Dir.mktmpdir('wingman-chart-') do |dir|
  run('helm', 'package', CHART, '--destination', dir)
  package = Dir[File.join(dir, '*.tgz')].fetch(0)
  run('helm', 'lint', '--strict', package)
  run('helm', 'lint', '--strict', package, *backend_args)
  check(verify(package) == starter, 'Packaged starter differs from source')
  check(verify(package, *backend_args) == backend, 'Packaged backend differs from source')
  [[], backend_args].each do |args|
    check(run('helm', 'template', 'review', package, *args) == run('helm', 'template', 'review', CHART, *args), 'Packaged manifests differ from source')
  end
end
puts 'Helm validation passed: 4 strict lints; default deployment parity; starter/backend renders; 4 incomplete configurations rejected; packaged/source manifest parity.'
