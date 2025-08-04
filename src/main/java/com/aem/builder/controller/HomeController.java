package com.aem.builder.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class HomeController {


    @GetMapping("/")
    public String test(Model model) throws IOException {

        return "dashboard";

    }


}

