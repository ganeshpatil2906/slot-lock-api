package com.gp.slotsync.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for Browse Slots tab.
 */
public class SlotsPage extends BasePage {

    private final Locator tabSection;
    private final Locator heading;
    private final Locator filterResource;
    private final Locator filterStatus;
    private final Locator refreshButton;
    private final Locator slotCards;

    public SlotsPage(Page page) {
        super(page);
        this.tabSection = page.locator("#slotsTab");
        this.heading = page.locator("#slotsTab .panel-title h2");
        this.filterResource = page.locator("#filterResource");
        this.filterStatus = page.locator("#filterStatus");
        this.refreshButton = page.locator("#slotsTab .panel-header button");
        this.slotCards = page.locator("#slotsGrid .card");
    }

    public boolean isTabActive() {
        return tabSection.getAttribute("class").contains("active");
    }

    public String getHeadingText() {
        return heading.innerText().trim();
    }

    public void selectStatusFilter(String status) {
        filterStatus.selectOption(status);
    }

    public void selectResourceFilter(String resourceValue) {
        filterResource.selectOption(resourceValue);
    }

    public void clickRefresh() {
        refreshButton.click();
    }

    public int getSlotCardsCount() {
        return slotCards.count();
    }

    public Locator getSlotCards() {
        return slotCards;
    }
}
