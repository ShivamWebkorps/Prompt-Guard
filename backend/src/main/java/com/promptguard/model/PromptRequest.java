package com.promptguard.model;

public class PromptRequest {

    private String userId;
    private String subUser;
    private String tool;
    private String browserName;
    private String prompt;
    private boolean testing;
    private String timestamp;

    public boolean isTesting()             { return testing; }
    public void    setTesting(boolean v)   { this.testing = v; }

    public String getUserId()              { return userId; }
    public void   setUserId(String v)      { this.userId = v; }

    public String getSubUser()             { return subUser; }
    public void   setSubUser(String v)     { this.subUser = v; }

    public String getTool()                { return tool; }
    public void   setTool(String v)        { this.tool = v; }

    public String getBrowserName()         { return browserName; }
    public void   setBrowserName(String v) { this.browserName = v; }

    public String getPrompt()              { return prompt; }
    public void   setPrompt(String v)      { this.prompt = v; }

    public String getTimestamp()           { return timestamp; }
    public void   setTimestamp(String v)   { this.timestamp = v; }
}
