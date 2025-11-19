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
     * Deploys the specified project and streams deployment logs in real-time
     * using the default deployment type (full).
     *
     * @param projectName the name of the project to deploy; must not be null or empty
     * @return a {@link Flux<String>} emitting deployment log lines in real-time
     * @throws RuntimeException if the deployment process cannot be started
     */
    default Flux<String> deployProjectLive(String projectName) {
        return deployProjectLive(projectName, "full");
    }

    /**
     * Deploys the specified project and streams deployment logs in real-time.
     *
     * @param projectName the name of the project to deploy; must not be null or empty
     * @param deployType  the deployment type, expected values: "full" or "core"
     * @return a {@link Flux<String>} emitting deployment log lines in real-time
     * @throws RuntimeException if the deployment process cannot be started
     */
    Flux<String> deployProjectLive(String projectName, String deployType);
}
