package com.aem.builder.controller;

import com.aem.builder.service.ComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aem.builder.service.ComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ComponentController {

    private final ComponentService componentService;

    @GetMapping("/fetch-components/{projectname}")
    @ResponseBody
    public Map<String, List<String>> getComponents(@PathVariable String projectname) throws IOException {
        List<String> allComponents = componentService.getAllComponents();
        List<String> projectComponents = componentService
                .getProjectComponentsMap(List.of(projectname))
                .getOrDefault(projectname, new ArrayList<>());


        log.info(projectname);

        List<String> distinctComponents = componentService.getDistinctComponents(allComponents, projectComponents);
        List<String> commonComponents = componentService.getCommonComponents(allComponents, projectComponents);

        log.info("{}",distinctComponents);
        log.info("{}",commonComponents);


        Map<String, List<String>> response = new HashMap<>();
        response.put("unique", distinctComponents);
        response.put("duplicate", commonComponents);

        return response;
    }


    @PostMapping("/add-components/{projectname}")
    public String addComponentsToExistingProject(
            @PathVariable String projectname,
            @RequestBody List<String> selectedComponents) {

        log.info(projectname);
        log.info(selectedComponents.toString());

        try {
            componentService.addComponentsToExistingProject(projectname, selectedComponents);
            return "dashboard";
        } catch (Exception e) {
            return "create";
        }
    }




}


