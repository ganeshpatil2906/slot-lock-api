package com.gp.slotsync.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Base Page Object containing common browser actions and helper methods.
 */
public abstract class BasePage {

    protected final Page page;

    public BasePage(Page page) {
        this.page = page;
    }

    public Page getPage() {
        return page;
    }

    public void navigate(String url) {
        page.navigate(url);
    }

    public String getTitle() {
        return page.title();
    }

    public String getUrl() {
        return page.url();
    }

    public void waitForTimeout(double milliseconds) {
        page.waitForTimeout(milliseconds);
    }

    public boolean isVisible(String selector) {
        try {
            return page.locator(selector).first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public void waitForSelector(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
    }

    public String getToastMessage() {
        Locator toast = page.locator("#toastContainer .toast").last();
        toast.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return toast.innerText().trim();
    }
}
