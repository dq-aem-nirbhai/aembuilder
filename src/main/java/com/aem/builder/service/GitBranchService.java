package com.aem.builder.service;

import java.nio.file.Path;
import java.util.List;

public interface GitBranchService {
    public String getCurrentBranch(Path projectPath);

    public List<String> listBranches(Path projectPath);

    public boolean hasStash(Path repoPath) throws Exception;

    public void switchBranch(Path projectPath, String branchName) throws Exception;

    public void createAndSwitchBranch(Path projectPath, String branchName) throws Exception;

    public void stashAndSwitch(Path repoPath, String branchName) throws Exception;

    public void unstash(Path repoPath) throws Exception;

    public void discardAndSwitch(Path repoPath, String branchName) throws Exception;

    public void mergeAndSwitch(Path repoPath, String branchName) throws Exception;
}
