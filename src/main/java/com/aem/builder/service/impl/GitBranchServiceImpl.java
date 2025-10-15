package com.aem.builder.service.impl;

import com.aem.builder.service.GitBranchService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.util.List;

import static com.aem.builder.constants.GitConstants.*;

/**
 * Implementation of {@link GitBranchService} using JGit.
 * <p>
 * Provides branch management (create, switch, merge, discard) and stash handling
 * (stash, unstash, check stash) for Git repositories.
 * <p>
 * All methods ensure proper resource management via try-with-resources
 * and log activity with INFO/ERROR level using SLF4J.
 */
@Slf4j
@Service
public class GitBranchServiceImpl implements GitBranchService {

    private static final String LOG_PREFIX = "[GitBranchService] ";

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCurrentBranch(Path projectPath) {
        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(projectPath.resolve(DOT_GIT).toFile())
                .build()) {

            String branch = repo.getBranch();
            log.info(LOG_PREFIX + "Current branch for repo {}: {}", projectPath, branch);
            return branch;
        } catch (Exception e) {
            log.error(LOG_PREFIX + "Failed to get current branch for repo {}", projectPath, e);
            return UNKNOWN_BRANCH;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listBranches(Path projectPath) {
        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(projectPath.resolve(DOT_GIT).toFile())
                .build();
             Git git = new Git(repo)) {

            List<String> branches = git.branchList().call().stream()
                    .map(Ref::getName)
                    .map(ref -> ref.replace(REFS_HEADS, ""))
                    .toList();

            log.info(LOG_PREFIX + "Branches for repo {}: {}", projectPath, branches);
            return branches;
        } catch (Exception e) {
            log.error(LOG_PREFIX + "Failed to list branches for repo {}", projectPath, e);
            return List.of();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasStash(Path repoPath) {
        try (Git git = Git.open(repoPath.toFile())) {
            List<RevCommit> stashes = (List<RevCommit>) git.stashList().call();
            boolean hasStash = !stashes.isEmpty();
            log.info(LOG_PREFIX + "Repo {} has stash: {}", repoPath, hasStash);
            return hasStash;
        } catch (Exception e) {
            log.error(LOG_PREFIX + "Failed to check stash for repo {}", repoPath);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchBranch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            log.info(LOG_PREFIX + "Switching to branch '{}' in repo {}", branchName, repoPath);
            git.checkout().setName(branchName).call();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stashAndSwitch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            log.info(LOG_PREFIX + "Stashing changes and switching to branch '{}' in repo {}", branchName, repoPath);
            git.stashCreate().call();
            git.checkout().setName(branchName).call();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unstash(Path repoPath) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            List<RevCommit> stashes = (List<RevCommit>) git.stashList().call();
            if (stashes.isEmpty()) {
                log.warn(LOG_PREFIX + "No stashes found in repo {}", repoPath);
                throw new IllegalStateException("No stashes available to apply");
            }

            log.info(LOG_PREFIX + "Applying and dropping latest stash in repo {}", repoPath);
            git.stashApply().setStashRef(STASH_REF).call();
            git.stashDrop().setStashRef(0).call();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void discardAndSwitch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            log.info(LOG_PREFIX + "Discarding changes and switching to branch '{}' in repo {}", branchName, repoPath);
            git.reset().setMode(ResetCommand.ResetType.HARD).call(); // discard local changes
            git.clean().setForce(true).call();                       // remove untracked files
            git.checkout().setName(branchName).call();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mergeAndSwitch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            log.info(LOG_PREFIX + "Committing WIP, switching, and merging branch '{}' in repo {}", branchName, repoPath);

            // commit current changes
            git.add().addFilepattern(".").call();
            git.commit().setMessage(WIP_COMMIT_MESSAGE).call();

            // checkout target branch
            git.checkout().setName(branchName).call();

            // merge previous branch
            String currentBranch = git.getRepository().getBranch();
            git.merge()
                    .include(git.getRepository().findRef(currentBranch))
                    .setCommit(true)
                    .setMessage("Merging changes from " + currentBranch)
                    .call();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createAndSwitchBranch(Path repoPath, String branchName, String fromBranch) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            log.info(LOG_PREFIX + "Creating branch '{}' from '{}' in repo {}", branchName, fromBranch, repoPath);

            boolean branchExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/" + branchName));

            if (branchExists) {
                log.info(LOG_PREFIX + "Branch '{}' already exists, switching...", branchName);
                git.checkout().setName(branchName).call();
                return;
            }

            // validate fromBranch exists
            boolean fromExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/" + fromBranch));

            String startPoint = fromExists ? (REFS_HEADS + fromBranch) : fromBranch;

            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(startPoint)
                    .call();
        }
    }
}
