package com.gp.slotsync.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for Admin Management Panel.
 */
public class AdminPage extends BasePage {

    private final Locator tabSection;
    private final Locator resNameInput;
    private final Locator resTypeInput;
    private final Locator createResourceBtn;
    private final Locator slotResourceSelect;
    private final Locator slotStartTimeInput;
    private final Locator slotEndTimeInput;
    private final Locator publishSlotBtn;

    public AdminPage(Page page) {
        super(page);
        this.tabSection = page.locator("#adminTab");
        this.resNameInput = page.locator("#resName");
        this.resTypeInput = page.locator("#resType");
        this.createResourceBtn = page.locator("#createResourceForm button");
        this.slotResourceSelect = page.locator("#slotResourceId");
        this.slotStartTimeInput = page.locator("#slotStartTime");
        this.slotEndTimeInput = page.locator("#slotEndTime");
        this.publishSlotBtn = page.locator("#createSlotForm button");
    }

    public boolean isTabActive() {
        return tabSection.getAttribute("class").contains("active");
    }

    public void createResource(String name, String type) {
        resNameInput.fill(name);
        resTypeInput.fill(type);
        createResourceBtn.click();
    }

    public void createSlot(String resourceId, String startIso, String endIso) {
        slotResourceSelect.selectOption(resourceId);
        slotStartTimeInput.fill(startIso);
        slotEndTimeInput.fill(endIso);
        publishSlotBtn.click();
    }
}
