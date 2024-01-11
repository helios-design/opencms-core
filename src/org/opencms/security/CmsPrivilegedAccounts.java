package org.opencms.security;

import java.util.Arrays;
import java.util.List;

/**
 * Provide a list of user accounts that are privileged and cannot be logged in via the OpenCms workplace
 */

public class CmsPrivilegedAccounts {
    // constants
    public static final String INVALID_USERNAME = "invalid-username";
    public static final List<String> PRIVILEGED_ACCOUNTS = Arrays.asList(
            "ai-scorebox-reader",
            "cdh-reader",
            "disputes-planning-reader",
            "dla_user_admin",
            "genie-reader",
            "it-healthcheck-reader",
            "pro-bono-reader",
            "privacy-scorebox-reader",
            "super_user"
    );

    /**
     * check the provided username and replace with invalid if disallowed
     * @param username
     * @return
     */
    public static String checkIfBlocked(String username) {
        if (PRIVILEGED_ACCOUNTS.contains(username)) {
            System.out.println("ATTEMPTED LOGIN of privileged account '" + username + "' was blocked");
            return INVALID_USERNAME;
        }
        return username;
    }
}
