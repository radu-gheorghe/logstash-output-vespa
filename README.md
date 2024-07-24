# Logstash Ouput Plugin for Vespa

Plugin for [Logstash](https://github.com/elastic/logstash) to write to [Vespa](https://vespa.ai). Apache 2.0 license.

## Installation

Download and unpack/install Logstash, then:
```
bin/logstash-plugin install logstash-output-vespa
```

## Development
If you're developing the plugin, you'll want to do something like:
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
Some more good info can be found [here](https://www.elastic.co/guide/en/logstash/current/output-new-plugin.html).
## Usage

Logstash config example:

```
# read stuff
input {
  # if you want to just send stuff to a "message" field from the terminal
  #stdin {}

  file {
    # let's assume we have some data in a CSV file here
    path => "/path/to/data.csv"
    # read the file from the beginning
    start_position => "beginning"
    # on Logstash restart, forget where we left off and start over again
    sincedb_path => "/dev/null"
  }
}

# parse and transform data here
filter {
  csv {
    # how does the CSV file look like?
    separator => ","
    quote_char => '"'

    # if the first line is the header, we'll skip it
    skip_header => true

    # columns of the CSV file. Make sure you have these fields in the Vespa schema
    columns => ["id", "description", ...]
  }

  # remove fields that we don't need. Here you can do a lot more processing
  mutate {
    remove_field => ["@timestamp", "@version", "event", "host", "log", "message"]
  }
}

# publish to Vespa
output {
  # for debugging. You can have multiple outputs (just as you can have multiple inputs/filters)
  #stdout {}

  vespa {
    # Vespa endpoint, namespace, doc type (from the schema)
    vespa_url => "http://localhost:8080"
    namespace => "my_namespace"
    document_type => "my_doc_type"

    # take the document ID from this field in each row
    # if the field doesn't exist, we generate a UUID
    id_field => "id"

    # retry on failure. We have exponential backoff (2s * retry_count)
    max_retries => 60
  }
}
```

Then you can start Logstash while pointing to the config file like:
```
bin/logstash -f logstash.conf
```

Or you can reference this config file in `logstash.yml` along with other settings. E.g. a [dead letter queue](https://www.elastic.co/guide/en/logstash/current/dead-letter-queues.html), in case we exceed the retry count or there's an unrecoverable error. That `logstash.yml` should be picked up automatically when you do `bin/logstash`. Or if you install [Logstash as a service](https://www.elastic.co/guide/en/logstash/current/running-logstash.html) (in which case `logstash.yml` goes to `/etc/logstash`).