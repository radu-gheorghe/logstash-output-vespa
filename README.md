# Logstash Plugin

This is a plugin for [Logstash](https://github.com/elastic/logstash).

It is fully free and fully open source. The license is Apache 2.0, meaning you are pretty much free to use it however you want in whatever way.

## Installation

If you're developing it, then:
```
# get the dependencies
bundle install
# build the gem
gem build logstash-output-vespa.gemspec
# install it as a Logstash plugin
/opt/logstash/bin/logstash-plugin install /path/to/logstash-output-vespa/logstash-output-vespa-$VERSION.gem
# profit
/opt/logstash/bin/logstash
```

## Usage

In your Logstash config:

```
output {
  vespa {
    vespa_url => "http://localhost:8080"
    namespace => "used_car"
    document_type => "used_car"
    id_field => "id"
    max_retries => 60
  }
}
```