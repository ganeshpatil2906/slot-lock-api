package com.gp.slotsync.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object representing the Navigation Bar and top Tab navigation.
 */
public class NavigationBar extends BasePage {

    private final Locator brandTitle;
    private final Locator authButton;
    private final Locator userBadge;
    private final Locator logoutButton;
    private final Locator slotsTabBtn;
    private final Locator resourcesTabBtn;
    private final Locator myBookingsTabBtn;
    private final Locator adminTabBtn;

    public NavigationBar(Page page) {
        super(page);
        this.brandTitle = page.locator(".brand span");
        this.authButton = page.locator("#navUserSection button:has-text('Login / Register')");
        this.userBadge = page.locator("#navUserSection .user-badge");
        this.logoutButton = page.locator("#navUserSection button:has-text('Logout')");
        this.slotsTabBtn = page.locator("button[data-tab='slotsTab']");
        this.resourcesTabBtn = page.locator("button[data-tab='resourcesTab']");
        this.myBookingsTabBtn = page.locator("button[data-tab='myBookingsTab']");
        this.adminTabBtn = page.locator("#adminTabBtn");
    }

    public String getBrandTitle() {
        return brandTitle.innerText().trim();
    }

    public AuthModalPage clickLoginRegister() {
        authButton.click();
        return new AuthModalPage(page);
    }

    public void clickBrowseSlotsTab() {
        slotsTabBtn.click();
    }

    public void clickResourcesTab() {
        resourcesTabBtn.click();
    }

    public void clickMyBookingsTab() {
        myBookingsTabBtn.click();
    }

    public void clickAdminTab() {
        adminTabBtn.click();
    }

    public boolean isAdminTabVisible() {
        return adminTabBtn.isVisible();
    }

    public boolean isLoggedIn() {
        try {
            userBadge.waitFor(new Locator.WaitForOptions().setTimeout(3000));
            return userBadge.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public String getLoggedInUserName() {
        if (userBadge.isVisible()) {
            return userBadge.innerText().trim();
        }
        return "";
    }

    public void clickLogout() {
        if (logoutButton.isVisible()) {
            logoutButton.click();
        }
    }
}
