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

import static com.aem.builder.constants.ModelAttributeKeys.*;
import static com.aem.builder.constants.UrlMappings.*; // URL constants
import static com.aem.builder.constants.AemProjectConstants.PROJECTS_DIR;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GitBranchController {

    private final GitBranchService gitBranchService;

    // ------------------- SWITCH BRANCH -------------------
    // Switch the project repository to the specified branch. Handles conflicts if any.
    @PostMapping(SWITCH_BRANCH_URL)
    public String switchBranch(@PathVariable String projectName,
                               @RequestParam String branchName,
                               RedirectAttributes redirectAttributes) {

        log.info("[switchBranch] Switching project '{}' to branch '{}'", projectName, branchName);

        try {
            gitBranchService.switchBranch(Paths.get(PROJECTS_DIR, projectName), branchName);
            redirectAttributes.addFlashAttribute(MESSAGE, "Switched to branch: " + branchName);
            log.info("[switchBranch] Successfully switched to '{}'", branchName);
        } catch (CheckoutConflictException e) {
            redirectAttributes.addFlashAttribute(CONFLICTS, e.getConflictingPaths());
            redirectAttributes.addFlashAttribute(TARGET_BRANCH, branchName);
            log.warn("[switchBranch] Conflicts detected while switching to '{}'", branchName);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR, "Error switching branch: " + e.getMessage());
            log.error("[switchBranch] Error switching branch '{}': {}", branchName, e.getMessage());
        }

        return REDIRECT_VIEW_PROJECT_URL.replace("{projectName}", projectName);
    }

    // ------------------- RESOLVE CONFLICTS -------------------
    // Resolve conflicts using stash, discard, or merge strategies, then switch to target branch.
    @PostMapping(RESOLVE_CONFLICTS_URL)
    public String resolveConflicts(@PathVariable String projectName,
                                   @RequestParam String action,
                                   @RequestParam String targetBranch,
                                   RedirectAttributes redirectAttributes) {

        log.info("[resolveConflicts] Resolving conflicts for project '{}' with action '{}' to branch '{}'",
                projectName, action, targetBranch);

        try {
            switch (action) {
                case "stash" -> {
                    gitBranchService.stashAndSwitch(Paths.get(PROJECTS_DIR, projectName), targetBranch);
                    redirectAttributes.addFlashAttribute(MESSAGE, "Stashed changes and switched to " + targetBranch);
                    log.info("[resolveConflicts] Stashed and switched to '{}'", targetBranch);
                }
                case "discard" -> {
                    gitBranchService.discardAndSwitch(Paths.get(PROJECTS_DIR, projectName), targetBranch);
                    redirectAttributes.addFlashAttribute(MESSAGE, "Discarded changes and switched to " + targetBranch);
                    log.info("[resolveConflicts] Discarded and switched to '{}'", targetBranch);
                }
                case "merge" -> {
                    gitBranchService.mergeAndSwitch(Paths.get(PROJECTS_DIR, projectName), targetBranch);
                    redirectAttributes.addFlashAttribute(MESSAGE, "Merged changes and switched to " + targetBranch);
                    log.info("[resolveConflicts] Merged and switched to '{}'", targetBranch);
                }
                default -> {
                    redirectAttributes.addFlashAttribute(ERROR, "Unknown action: " + action);
                    log.warn("[resolveConflicts] Unknown action '{}'", action);
                }
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR, "Conflict resolution failed: " + e.getMessage());
            log.error("[resolveConflicts] Conflict resolution failed: {}", e.getMessage());
        }

        return REDIRECT_VIEW_PROJECT_URL.replace("{projectName}", projectName);
    }

    // ------------------- UNSTASH -------------------
    // Apply previously stashed changes to the project repository.
    @PostMapping(UNSTASH_URL)
    public String unstash(@PathVariable String projectName, RedirectAttributes redirectAttributes) {

        log.info("[unstash] Applying stashed changes for project '{}'", projectName);

        try {
            gitBranchService.unstash(Paths.get(PROJECTS_DIR, projectName));
            redirectAttributes.addFlashAttribute(MESSAGE, "Applied stashed changes.");
            log.info("[unstash] Successfully applied stashed changes");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ERROR, e.getMessage());
            log.warn("[unstash] Illegal state: {}", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR, "Failed to unstash: " + e.getMessage());
            log.error("[unstash] Failed to unstash: {}", e.getMessage());
        }

        return REDIRECT_VIEW_PROJECT_URL.replace("{projectName}", projectName);
    }

    // ------------------- CREATE BRANCH -------------------
    // Create a new branch from a specified branch and switch to it.
    @PostMapping(CREATE_BRANCH_URL)
    public String createBranch(@PathVariable String projectName,
                               @RequestParam String newBranch,
                               @RequestParam String fromBranch,
                               RedirectAttributes redirectAttributes) {

        log.info("[createBranch] Creating new branch '{}' from '{}' for project '{}'",
                newBranch, fromBranch, projectName);

        try {
            gitBranchService.createAndSwitchBranch(Paths.get(PROJECTS_DIR, projectName), newBranch, fromBranch);
            redirectAttributes.addFlashAttribute(MESSAGE, "Created and switched to branch: " + newBranch);
            log.info("[createBranch] Successfully created and switched to '{}'", newBranch);
        } catch (CheckoutConflictException e) {
            // send conflicts back to UI and indicate this was a create action
            redirectAttributes.addFlashAttribute(CONFLICTS, e.getConflictingPaths());
            redirectAttributes.addFlashAttribute(TARGET_BRANCH, newBranch);
            redirectAttributes.addFlashAttribute(BRANCH_ACTION, "create");
            redirectAttributes.addFlashAttribute(FROM_BRANCH, fromBranch);
            log.warn("[createBranch] Conflicts detected while creating branch '{}'", newBranch);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR, "Failed to create branch: " + e.getMessage());
            log.error("[createBranch] Failed to create branch '{}': {}", newBranch, e.getMessage());
        }

        return REDIRECT_VIEW_PROJECT_URL.replace("{projectName}", projectName);
    }
}
