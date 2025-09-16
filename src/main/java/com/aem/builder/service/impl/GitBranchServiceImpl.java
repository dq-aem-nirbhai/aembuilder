package com.aem.builder.service.impl;

import com.aem.builder.service.GitBranchService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class GitBranchServiceImpl implements GitBranchService {

    @Override
    public String getCurrentBranch(Path projectPath) {
        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(projectPath.resolve(".git").toFile())
                .build()) {
            return repo.getBranch();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public List<String> listBranches(Path projectPath) {
        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(projectPath.resolve(".git").toFile())
                .build();
             Git git = new Git(repo)) {
            return git.branchList().call().stream()
                    .map(Ref::getName)
                    .map(ref -> ref.replace("refs/heads/", ""))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public boolean hasStash(Path repoPath) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            List<RevCommit> stashes = (List<RevCommit>) git.stashList().call();
            return !stashes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void switchBranch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.checkout().setName(branchName).call();
        }
    }

    @Override
    public void stashAndSwitch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.stashCreate().call();
            git.checkout().setName(branchName).call();
        }
    }

    @Override
    public void unstash(Path repoPath) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            List<RevCommit> stashes = (List<RevCommit>) git.stashList().call();
            if (stashes.isEmpty()) {
                throw new IllegalStateException("No stashes available to apply");
            }

            git.stashApply().setStashRef("stash@{0}").call();
            git.stashDrop().setStashRef(0).call();
        }
    }

    public void discardAndSwitch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.reset().setMode(ResetCommand.ResetType.HARD).call(); // discard local changes
            git.clean().setForce(true).call();                       // remove untracked files
            git.checkout().setName(branchName).call();
        }
    }

    public void mergeAndSwitch(Path repoPath, String branchName) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            // commit current changes
            git.add().addFilepattern(".").call();
            git.commit().setMessage("WIP before switching").call();

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

    @Override
    public void createAndSwitchBranch(Path repoPath, String branchName, String fromBranch) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            boolean branchExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/" + branchName));

            if (branchExists) {
                // branch already exists, just switch
                git.checkout().setName(branchName).call();
                return;
            }

            // validate fromBranch exists
            boolean fromExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("/" + fromBranch));

            String startPoint = fromExists ? ("refs/heads/" + fromBranch) : fromBranch;

            // create a new branch from the startPoint and switch to it
            // This may throw CheckoutConflictException if the working tree has conflicting changes
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setStartPoint(startPoint)
                    .call();
        }
    }

}
