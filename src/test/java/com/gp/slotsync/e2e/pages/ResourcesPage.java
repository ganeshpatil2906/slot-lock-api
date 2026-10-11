package com.gp.slotsync.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for Bookable Resources tab.
 */
public class ResourcesPage extends BasePage {

    private final Locator tabSection;
    private final Locator heading;
    private final Locator addResourceBtn;
    private final Locator resourcesGrid;
    private final Locator resourceCards;

    public ResourcesPage(Page page) {
        super(page);
        this.tabSection = page.locator("#resourcesTab");
        this.heading = page.locator("#resourcesTab .panel-title h2");
        this.addResourceBtn = page.locator("#createResourceBtn");
        this.resourcesGrid = page.locator("#resourcesGrid");
        this.resourceCards = page.locator("#resourcesGrid .card");
    }

    public boolean isTabActive() {
        return tabSection.getAttribute("class").contains("active");
    }

    public String getHeadingText() {
        return heading.innerText().trim();
    }

    public boolean isAddResourceButtonVisible() {
        return addResourceBtn.isVisible();
    }

    public int getResourceCardsCount() {
        return resourceCards.count();
    }
}
