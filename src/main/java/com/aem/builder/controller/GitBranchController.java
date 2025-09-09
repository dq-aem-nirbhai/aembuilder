package com.aem.builder.controller;

import com.aem.builder.service.GitBranchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Paths;


@Controller
@RequiredArgsConstructor
@Slf4j
public class GitBranchController {

    private final GitBranchService gitBranchService;

    private static final String PROJECTS_DIR = "generated-projects";


    @PostMapping("/{projectName}/switchBranch")
    public String switchBranch(
            @PathVariable String projectName,
            @RequestParam String branchName,
            RedirectAttributes redirectAttributes) {

        try {
            gitBranchService.switchBranch(Paths.get(PROJECTS_DIR, projectName), branchName);
            redirectAttributes.addFlashAttribute("message", "Switched to branch: " + branchName);
        } catch (CheckoutConflictException e) {
            redirectAttributes.addFlashAttribute("conflicts", e.getConflictingPaths());
            redirectAttributes.addFlashAttribute("targetBranch", branchName);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error switching branch: " + e.getMessage());
        }

        return "redirect:/view/" + projectName;
    }

    @PostMapping("/{projectName}/resolveConflicts")
    public String resolveConflicts(
            @PathVariable String projectName,
            @RequestParam String action,
            @RequestParam String targetBranch,
            RedirectAttributes redirectAttributes) {

        try {
            switch (action) {
                case "stash":
                    gitBranchService.stashAndSwitch(Paths.get(PROJECTS_DIR, projectName), targetBranch);
                    redirectAttributes.addFlashAttribute("message", "Stashed changes and switched to " + targetBranch);
                    break;

                case "discard":
                    gitBranchService.discardAndSwitch(Paths.get(PROJECTS_DIR, projectName), targetBranch);
                    redirectAttributes.addFlashAttribute("message", "Discarded changes and switched to " + targetBranch);
                    break;

                case "merge":
                    gitBranchService.mergeAndSwitch(Paths.get(PROJECTS_DIR, projectName), targetBranch);
                    redirectAttributes.addFlashAttribute("message", "Merged changes and switched to " + targetBranch);
                    break;

                default:
                    redirectAttributes.addFlashAttribute("error", "Unknown action: " + action);
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Conflict resolution failed: " + e.getMessage());
        }

        return "redirect:/view/" + projectName; // back to details page
    }

    @PostMapping("/{projectName}/unstash")
    public String unstash(@PathVariable String projectName, RedirectAttributes redirectAttributes) {
        try {
            gitBranchService.unstash(Paths.get(PROJECTS_DIR, projectName));
            redirectAttributes.addFlashAttribute("message", "Applied stashed changes.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to unstash: " + e.getMessage());
        }
        return "redirect:/view/" + projectName;
    }

    @PostMapping("/{projectName}/createBranch")
    public String createBranch(@PathVariable String projectName,
                               @RequestParam String newBranch,
                               RedirectAttributes redirectAttributes) {
        try {
            gitBranchService.createAndSwitchBranch(Paths.get(PROJECTS_DIR, projectName), newBranch);
            redirectAttributes.addFlashAttribute("message", "Created and switched to branch: " + newBranch);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to create branch: " + e.getMessage());
        }
        return "redirect:/" + projectName + "/details";
    }

}
