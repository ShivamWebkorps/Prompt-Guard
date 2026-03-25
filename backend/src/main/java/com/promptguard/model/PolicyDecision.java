package com.promptguard.model;

public class PolicyDecision {

    private Action action;
    private String reason;

    public PolicyDecision() {}

    public PolicyDecision(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    public Action getAction()           { return action; }
    public void   setAction(Action v)   { this.action = v; }

    public String getReason()           { return reason; }
    public void   setReason(String v)   { this.reason = v; }
}
