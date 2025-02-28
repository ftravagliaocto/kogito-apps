/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.explainability.messaging;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.kie.kogito.cloudevents.CloudEventUtils;
import org.kie.kogito.explainability.ExplanationService;
import org.kie.kogito.explainability.api.BaseExplainabilityRequestDto;
import org.kie.kogito.explainability.api.BaseExplainabilityResultDto;
import org.kie.kogito.explainability.handlers.LocalExplainerServiceHandlerRegistry;
import org.kie.kogito.explainability.models.BaseExplainabilityRequest;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

@ApplicationScoped
public class ExplainabilityMessagingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainabilityMessagingHandler.class);

    private static final URI URI_PRODUCER = URI.create("explainabilityService/ExplainabilityMessagingHandler");

    private final BroadcastProcessor<String> eventSubject = BroadcastProcessor.create();

    protected ExplanationService explanationService;
    protected LocalExplainerServiceHandlerRegistry explainerServiceHandlerRegistry;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    public ExplainabilityMessagingHandler(
            ExplanationService explanationService,
            LocalExplainerServiceHandlerRegistry explainerServiceHandlerRegistry) {
        this.explanationService = explanationService;
        this.explainerServiceHandlerRegistry = explainerServiceHandlerRegistry;
    }

    // Incoming
    @Incoming("trusty-explainability-request")
    public CompletionStage<Void> handleMessage(Message<String> message) {
        try {
            Optional<CloudEvent> cloudEventOpt = CloudEventUtils.decode(message.getPayload());
            if (!cloudEventOpt.isPresent()) {
                return message.ack();
            }

            CloudEvent cloudEvent = cloudEventOpt.get();
            return handleCloudEvent(cloudEvent)
                    .thenAccept(x -> message.ack());
        } catch (Exception e) {
            LOGGER.error("Something unexpected happened during the processing of an Event. The event is discarded.", e);
        }
        return message.ack();
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Void> handleCloudEvent(CloudEvent cloudEvent) {
        BaseExplainabilityRequestDto requestDto = null;
        try {
            if (cloudEvent.getData() != null) {
                requestDto = objectMapper.readValue(cloudEvent.getData().toBytes(), BaseExplainabilityRequestDto.class);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to deserialize CloudEvent data as ExplainabilityRequest", e);
            return CompletableFuture.completedFuture(null);
        }
        if (requestDto == null) {
            LOGGER.error("Received CloudEvent with id {} from {} with empty data", cloudEvent.getId(), cloudEvent.getSource());
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("Received CloudEvent with id {} from {}", cloudEvent.getId(), cloudEvent.getSource());

        BaseExplainabilityRequest request = explainerServiceHandlerRegistry.explainabilityRequestFrom(requestDto);

        return explanationService
                .explainAsync(request, this::sendEvent)
                .thenApply(this::sendEvent);
    }

    // Outgoing
    public Void sendEvent(BaseExplainabilityResultDto result) {
        //This should never happen but let's protect against it 
        if (Objects.isNull(result)) {
            LOGGER.info("Request received to send null result. Skipping.");
            return null;
        }

        LOGGER.info("Explainability service emits explainability {} for execution with ID {}",
                result.getClass().getSimpleName(),
                result.getExecutionId());
        Optional<String> optPayload = CloudEventUtils
                .build(result.getExecutionId(), URI_PRODUCER, result, BaseExplainabilityResultDto.class)
                .flatMap(CloudEventUtils::encode);
        if (optPayload.isPresent()) {
            eventSubject.onNext(optPayload.get());
        } else {
            LOGGER.warn("Ignoring empty CloudEvent");
        }
        return null;
    }

    @Outgoing("trusty-explainability-result")
    public Publisher<String> getEventPublisher() {
        return eventSubject.toHotStream();
    }
}
