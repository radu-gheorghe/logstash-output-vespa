# encoding: utf-8
require "logstash/devutils/rspec/spec_helper"
require "logstash/outputs/vespa"
require "logstash/codecs/plain"


describe LogStash::Outputs::Vespa do
  let(:sample_event) { LogStash::Event.new }
  let(:output) { LogStash::Outputs::Vespa.new }

  before do
    output.register
  end

  ## TODO add tests :)
  describe "receive message" do
    subject { output.receive(sample_event) }

    it "returns a string" do
      expect(subject).to eq("Event received")
    end
  end
end
