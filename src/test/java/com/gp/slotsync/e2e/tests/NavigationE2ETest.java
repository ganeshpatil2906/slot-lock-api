package com.gp.slotsync.e2e.tests;

import com.gp.slotsync.e2e.base.BaseTest;
import com.gp.slotsync.e2e.config.ConfigReader;
import com.gp.slotsync.e2e.pages.NavigationBar;
import com.gp.slotsync.e2e.pages.ResourcesPage;
import com.gp.slotsync.e2e.pages.SlotsPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NavigationE2ETest extends BaseTest {

    @Test(description = "Verify page title and brand branding in navbar")
    public void testAppHeaderAndBrandTitle() {
        getPage().navigate(ConfigReader.getBaseUrl());

        NavigationBar navBar = new NavigationBar(getPage());
        Assert.assertTrue(getPage().title().contains("SlotSync"), "Page title should contain SlotSync");
        Assert.assertEquals(navBar.getBrandTitle(), "SlotSync", "Brand title must display 'SlotSync'");
    }

    @Test(description = "Verify tab switching between Browse Slots, Resources, and My Bookings")
    public void testTabSwitching() {
        getPage().navigate(ConfigReader.getBaseUrl());

        NavigationBar navBar = new NavigationBar(getPage());
        SlotsPage slotsPage = new SlotsPage(getPage());
        ResourcesPage resourcesPage = new ResourcesPage(getPage());

        // Default tab is Browse Slots
        Assert.assertTrue(slotsPage.isTabActive(), "Default tab should be Browse Slots");
        Assert.assertEquals(slotsPage.getHeadingText(), "Available Time Slots");

        // Switch to Resources tab
        navBar.clickResourcesTab();
        Assert.assertTrue(resourcesPage.isTabActive(), "Resources tab should become active");
        Assert.assertEquals(resourcesPage.getHeadingText(), "Bookable Resources");

        // Switch to My Bookings tab
        navBar.clickMyBookingsTab();
        Assert.assertTrue(getPage().locator("#myBookingsTab").getAttribute("class").contains("active"),
                "My Bookings tab should become active");

        // Switch back to Browse Slots
        navBar.clickBrowseSlotsTab();
        Assert.assertTrue(slotsPage.isTabActive(), "Browse Slots tab should be active again");
    }

    @Test(description = "Verify Admin Actions tab is hidden for guest/unauthenticated users")
    public void testAdminTabHiddenForGuest() {
        getPage().navigate(ConfigReader.getBaseUrl());

        NavigationBar navBar = new NavigationBar(getPage());
        Assert.assertFalse(navBar.isAdminTabVisible(), "Admin tab should not be visible for guest users");
    }
}
