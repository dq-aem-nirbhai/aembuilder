package com.aem.builder.service;

import reactor.core.publisher.Flux;

/**
 * Service interface for handling deployment operations of AEM projects.
 */
public interface DeployService {

    /**
     * Backward-compatible single-arg method - defaults to full deploy.
     */
    default Flux<String> deployProjectLive(String projectName) {
        return deployProjectLive(projectName, "full");
    }

    /**
     * Deploys the specified project and streams deployment logs in real-time.
     *
     * @param projectName the name of the project to deploy
     * @param deployType  one of: "full", "core", "ui" (ui = ui.content)
     * @return a {@link Flux<String>} emitting deployment log lines in real-time
     */
    Flux<String> deployProjectLive(String projectName, String deployType);
}
