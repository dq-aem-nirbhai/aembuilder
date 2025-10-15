package com.aem.builder.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Service interface for managing Git branches and stash operations.
 * Provides methods for fetching, switching, creating, stashing,
 * and merging branches within a Git repository.
 */
public interface GitBranchService {

    /**
     * Get the name of the current active Git branch.
     *
     * @param projectPath path to the Git repository
     * @return current branch name
     */
    String getCurrentBranch(Path projectPath);

    /**
     * List all available branches in the repository.
     *
     * @param projectPath path to the Git repository
     * @return list of branch names
     */
    List<String> listBranches(Path projectPath);

    /**
     * Check if the repository has any stashed changes.
     *
     * @param repoPath path to the Git repository
     * @return true if stash exists, false otherwise
     * @throws Exception if Git command execution fails
     */
    boolean hasStash(Path repoPath) throws Exception;

    /**
     * Switch to the specified branch.
     *
     * @param projectPath path to the Git repository
     * @param branchName  branch name to switch to
     * @throws Exception if Git command execution fails
     */
    void switchBranch(Path projectPath, String branchName) throws Exception;

    /**
     * Create a new branch from the given source branch and switch to it.
     *
     * @param repoPath   path to the Git repository
     * @param branchName new branch name
     * @param fromBranch source branch to create from
     * @throws Exception if Git command execution fails
     */
    void createAndSwitchBranch(Path repoPath, String branchName, String fromBranch) throws Exception;

    /**
     * Stash current changes and switch to the given branch.
     *
     * @param repoPath   path to the Git repository
     * @param branchName branch name to switch to
     * @throws Exception if Git command execution fails
     */
    void stashAndSwitch(Path repoPath, String branchName) throws Exception;

    /**
     * Apply the most recent stash (unstash).
     *
     * @param repoPath path to the Git repository
     * @throws Exception if Git command execution fails
     */
    void unstash(Path repoPath) throws Exception;

    /**
     * Discard all local changes and switch to the given branch.
     *
     * @param repoPath   path to the Git repository
     * @param branchName branch name to switch to
     * @throws Exception if Git command execution fails
     */
    void discardAndSwitch(Path repoPath, String branchName) throws Exception;

    /**
     * Merge the specified branch into the current branch and switch to it.
     *
     * @param repoPath   path to the Git repository
     * @param branchName branch name to merge into the current branch
     * @throws Exception if Git command execution fails
     */
    void mergeAndSwitch(Path repoPath, String branchName) throws Exception;
}
