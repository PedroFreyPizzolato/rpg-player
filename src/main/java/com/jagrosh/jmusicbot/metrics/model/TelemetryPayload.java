/*
 * Copyright 2026 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.metrics.model;

import java.util.List;

/**
 * Represents the complete telemetry payload sent to the collection endpoint.
 *
 * @author John Grosh (jagrosh)
 */
public class TelemetryPayload {
    private final String collectionId;
    private final String instanceId;
    private final List<TelemetryEvent> events;

    /**
     * Creates a new telemetry payload.
     *
     * @param collectionId The ID of the collection request
     * @param instanceId   The unique instance ID
     * @param events       The list of telemetry events
     */
    public TelemetryPayload(String collectionId, String instanceId, List<TelemetryEvent> events) {
        this.collectionId = collectionId;
        this.instanceId = instanceId;
        this.events = events;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public List<TelemetryEvent> getEvents() {
        return events;
    }
}
