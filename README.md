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
# build the gem
./gradlew gem
# install it as a Logstash plugin
/opt/logstash/bin/logstash-plugin install /path/to/logstash-output-vespa/logstash-output-vespa-$VERSION.gem
# profit
/opt/logstash/bin/logstash
```
Some more good info can be found [here](https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html).
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

  vespa_feed { # including defaults here
  
    # Vespa endpoint, namespace, doc type (from the schema)
    vespa_url => "http://localhost:8080"
    namespace => "no_default_provide_yours"
    document_type => "no_default_provide_yours_from_schema"

    # take the document ID from this field in each row
    # if the field doesn't exist, we generate a UUID
    id_field => "id"

    # how many HTTP/2 connections to keep open
    max_connections => 1
    # number of streams per connection
    max_streams => 128
    # request timeout (seconds) for each write operation
    operation_timeout => 180
    # after this time (seconds), the circuit breaker will be half-open:
    # it will ping the endpoint to see if it's back,
    # then resume sending requests when it's back
    grace_period => 10
    
    # how many times to retry on transient failures
    max_retries => 10
  }
}
```

Then you can start Logstash while pointing to the config file like:
```
bin/logstash -f logstash.conf
```
