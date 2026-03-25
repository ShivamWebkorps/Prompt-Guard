package com.promptguard.model;

/**
 * Model for the user_keyword_policies table.
 */
public class UserKeywordPolicy {
    private int id;
    private String userId;
    private String subUser;
    private String keywordList;
    private boolean allowCol;
    private boolean redactedCol;
    private boolean criticalCol;
    private boolean blockCol;
    private String promptCol;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSubUser() { return subUser; }
    public void setSubUser(String subUser) { this.subUser = subUser; }

    public String getKeywordList() { return keywordList; }
    public void setKeywordList(String keywordList) { this.keywordList = keywordList; }

    public boolean isAllowCol() { return allowCol; }
    public void setAllowCol(boolean allowCol) { this.allowCol = allowCol; }

    public boolean isRedactedCol() { return redactedCol; }
    public void setRedactedCol(boolean redactedCol) { this.redactedCol = redactedCol; }

    public boolean isCriticalCol() { return criticalCol; }
    public void setCriticalCol(boolean criticalCol) { this.criticalCol = criticalCol; }

    public boolean isBlockCol() { return blockCol; }
    public void setBlockCol(boolean blockCol) { this.blockCol = blockCol; }

    public String getPromptCol() { return promptCol; }
    public void setPromptCol(String promptCol) { this.promptCol = promptCol; }
}
