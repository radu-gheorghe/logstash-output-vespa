# encoding: utf-8
require "logstash/outputs/base"
require "logstash/namespace"
require "net/http"
require "uri"
require "securerandom"

class LogStash::Outputs::Vespa < LogStash::Outputs::Base
  config_name "vespa"

  # URL to the Vespa instance
  config :vespa_url, :validate => :string, :default => "http://localhost:8080"

  # Vespa namespace. A logical grouping of documents in Vespa
  config :namespace, :validate => :string, :required => true

  # Document type. You should see it in the Vespa services.xml file as well as in the schema file
  config :document_type, :validate => :string, :required => true

  # Field to get the document id from. If not present, a UUID will be generated
  config :id_field, :validate => :string, :default => "id"

  # On failure, retry this many times
  config :max_retries, :validate => :number, :default => 60

  concurrency :shared

  public
  def register
    # initialize a dead letter queue writer
    @dlq_writer = execution_context.dlq_writer

    @base_uri = URI.parse(@vespa_url)
  end # def register

  public
  def receive(event)
    retry_attempts = @max_retries

    # if there's a document "id" field, we should use it as the document id
    # otherwise, we generate a UUID
    id = event.get(@id_field) || SecureRandom.uuid

    # build the path from the cluster name, document type and document id
    uri = @base_uri.dup
    uri.path = "/document/v1/#{@namespace}/#{@document_type}/docid/#{id}"

    http_client = Net::HTTP.new(uri.host, uri.port)
    request = Net::HTTP::Post.new(uri.request_uri, {'Content-Type' => 'application/json'})

    document = {
      "fields" => event.to_hash()
    }

    request.body = document.to_json
    response = http_client.request(request)

    # Retry on 429, 500, 502, 503, 504
    retry_count = 0
    while [429, 500, 502, 503, 504].include?(response.code.to_i) && retry_count < @max_retries
      @logger.warn("Received #{response.code} for path #{uri.path} and content #{document.to_json}. " \
        "Retrying... (attempt #{retry_count + 1}/#{@max_retries})")
      retry_count += 1
      sleep 2 ** retry_count # Exponential backoff
      response = http_client.request(request)
    end

    if response.code.to_i != 200
      @logger.error("Error sending event to Vespa. Writing to dead letter queue (if it's configured in logstash.yml).",
        :response_code => response.code, :response_body => response.body)
      @dlq_writer.write(event, response.body)
    end

    rescue => e
      # initialize retry count if it's not already set
      retry_count ||= 0
      if retry_count < @max_retries
        @logger.error("Exception caught while sending event to Vespa. Retrying... (attempt #{retry_count + 1}/#{@max_retries})",
          :exception => e, :event => event)
        retry_count += 1
        sleep 2 ** retry_count # Exponential backoff
        retry
      else
        @logger.error("Giving up on retrying. Writing to dead letter queue (if it's configured in logstash.yml).",
         :exception => e, :event => event)
        @dlq_writer.write(event, e.message)
      end
  end # def receive
end # class LogStash::Outputs::Vespa
