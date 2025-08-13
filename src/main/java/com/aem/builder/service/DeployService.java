package com.aem.builder.service;

import reactor.core.publisher.Flux;

public interface DeployService {
    Flux<String> deployProjectLive(String projectName);
}
