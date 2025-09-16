package com.aem.builder.service;

import reactor.core.publisher.Flux;

/**
 * Service interface for handling deployment operations of AEM projects.
 * <p>
 * This interface defines the contract for deploying projects and streaming
 * live deployment logs as reactive {@link Flux} events.
 * </p>
 */
public interface DeployService {

    /**
     * Deploys the specified project and streams deployment logs in real-time.
     * <p>
     * Implementations should handle execution of the deployment process (e.g., Maven commands),
     * capture standard output/error streams, and return them as a reactive {@link Flux<String>} for
     * consumption by the frontend (e.g., Server-Sent Events or WebSocket).
     * </p>
     *
     * @param projectName the name of the project to deploy; must not be null or empty
     * @return a {@link Flux<String>} emitting deployment log lines in real-time
     * @throws RuntimeException if the deployment process cannot be started
     */
    Flux<String> deployProjectLive(String projectName);
}
