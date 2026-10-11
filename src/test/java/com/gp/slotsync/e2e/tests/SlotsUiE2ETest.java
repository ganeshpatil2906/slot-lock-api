package com.gp.slotsync.e2e.tests;

import com.gp.slotsync.e2e.base.BaseTest;
import com.gp.slotsync.e2e.config.ConfigReader;
import com.gp.slotsync.e2e.pages.SlotsPage;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SlotsUiE2ETest extends BaseTest {

    @Test(description = "Verify slot filter controls for status and resource")
    public void testFilterControlsAvailable() {
        getPage().navigate(ConfigReader.getBaseUrl());

        SlotsPage slotsPage = new SlotsPage(getPage());
        Assert.assertTrue(slotsPage.isTabActive(), "Slots tab should be active");

        // Verify filter selection interaction
        slotsPage.selectStatusFilter("HELD");
        slotsPage.selectStatusFilter("AVAILABLE");
        
        Assert.assertNotNull(slotsPage.getHeadingText(), "Heading should be present");
    }

    @Test(description = "Verify refresh button interaction on slots tab")
    public void testRefreshButtonInteraction() {
        getPage().navigate(ConfigReader.getBaseUrl());

        SlotsPage slotsPage = new SlotsPage(getPage());
        slotsPage.clickRefresh();
        getPage().waitForTimeout(300);

        Assert.assertTrue(slotsPage.isTabActive(), "Slots tab should remain active after refresh");
    }
}
