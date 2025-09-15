package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class GitUtil {

    /** Setup local branches for all remote branches */
    public static void setupLocalBranches(Git git) throws Exception {
        List<Ref> remoteBranches = git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call();

        for (Ref remoteRef : remoteBranches) {
            String fullName = remoteRef.getName();
            if (fullName.startsWith("refs/remotes/origin/")) {
                String branchName = fullName.replace("refs/remotes/origin/", "");
                if ("HEAD".equals(branchName)) continue;

                boolean exists = git.branchList().call().stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + branchName));

                if (!exists) {
                    git.branchCreate()
                            .setName(branchName)
                            .setStartPoint(fullName)
                            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                            .call();
                    log.info("[GitUtil] Created local branch '{}'", branchName);
                }
            }
        }
    }

    /** Clone repository to temp dir */
    public static Git cloneRepository(String repoUrl, Path tempDir) throws Exception {
        log.info("[GitUtil] Cloning repository '{}' to '{}'", repoUrl, tempDir);
        return Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(tempDir.toFile())
                .setCloneAllBranches(true)
                .setBranch("refs/heads/main")
                .call();
    }
}
