source 'https://rubygems.org'
gemspec

logstash_path = ENV['LOGSTASH_PATH'] || '/opt/logstash/logstash-8.14.2'

if Dir.exist?(logstash_path)
  gem 'logstash-core', :path => "#{logstash_path}/logstash-core"
  gem 'logstash-core-plugin-api', :path => "#{logstash_path}/logstash-core-plugin-api"
end

