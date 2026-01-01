# trip-agent

Based on the [trip-agent](https://github.com/akka-samples/trip-agent) Akka example.

> This app represents an agency that searches for flights and accommodations via the prompt from the user through an HTTP call.
> 
> It's composed by a LLM Model and tools to find flights, accommodations and sending mails.
> 
> Once a search is requested the app will look for the flights, accommodations, and will email the requester with some options and the best value offer.
> 
> -- <cite>Akka / Tutorials / Additional Samples</cite>

## TO-DO List

* Add items to this list.

## Example

```shell
curl -X POST http://localhost:8989/trip-searches/01226N0640J7Q
```

## Contributing to the project

### Building and testing this project

```shell
sbt -v -Dfile.encoding=UTF-8 +check +test
```

### Building distribution from source code

```shell
sbt Universal/packageBin
```

## Resources:

(Listed in no specific order)

### Akka

* [Trip booking with tools](https://doc.akka.io/getting-started/samples.html#_trip_booking_with_tools).
* [LLM (Anthropic) with tools integration to find flights](https://github.com/akka-samples/trip-agent).

### BPMN

* [BPMN diagram editor and viewer](https://demo.bpmn.io/).

### Development

* [Workflows4s - Business-oriented Workflows for Scala](https://business4s.org/workflows4s/).
* [LangChain4j - Open-source library that simplifies the integration of LLMs into Java applications](https://docs.langchain4j.dev/).

## Troubleshooting

Check `scalac` compiler options with:

```sbt
show Compile / scalacOptions
```
