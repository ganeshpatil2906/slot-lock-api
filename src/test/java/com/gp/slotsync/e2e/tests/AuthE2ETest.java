package com.gp.slotsync.e2e.tests;

import com.gp.slotsync.e2e.base.BaseTest;
import com.gp.slotsync.e2e.config.ConfigReader;
import com.gp.slotsync.e2e.pages.AuthModalPage;
import com.gp.slotsync.e2e.pages.NavigationBar;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AuthE2ETest extends BaseTest {

    @Test(description = "Verify opening and toggling between Login and Registration modes in Auth Modal")
    public void testAuthModalToggleModes() {
        getPage().navigate(ConfigReader.getBaseUrl());

        NavigationBar navBar = new NavigationBar(getPage());
        AuthModalPage authModal = navBar.clickLoginRegister();

        Assert.assertTrue(authModal.isModalVisible(), "Auth Modal should be visible after clicking login button");
        Assert.assertEquals(authModal.getModalTitle(), "Welcome Back", "Default mode title should be 'Welcome Back'");
        Assert.assertFalse(authModal.isNameFieldVisible(), "Name field should be hidden in login mode");
        Assert.assertFalse(authModal.isRoleFieldVisible(), "Role select should be hidden in login mode");

        // Toggle to Register mode
        authModal.clickToggleMode();
        Assert.assertEquals(authModal.getModalTitle(), "Create Account", "Title should switch to 'Create Account'");
        Assert.assertTrue(authModal.isNameFieldVisible(), "Name field should be visible in registration mode");
        Assert.assertTrue(authModal.isRoleFieldVisible(), "Role select should be visible in registration mode");

        // Toggle back to Login mode
        authModal.clickToggleMode();
        Assert.assertEquals(authModal.getModalTitle(), "Welcome Back", "Title should return to 'Welcome Back'");
        Assert.assertFalse(authModal.isNameFieldVisible(), "Name field should be hidden again");
    }

    @Test(description = "Verify closing Auth Modal via close button")
    public void testCloseAuthModal() {
        getPage().navigate(ConfigReader.getBaseUrl());

        NavigationBar navBar = new NavigationBar(getPage());
        AuthModalPage authModal = navBar.clickLoginRegister();

        Assert.assertTrue(authModal.isModalVisible(), "Auth modal should be open");

        authModal.closeModal();
        getPage().waitForTimeout(300); // Allow CSS animation to complete

        Assert.assertFalse(authModal.isModalVisible(), "Auth modal should be closed after clicking close button");
    }

    @Test(description = "Verify login flow and user session navbar update")
    public void testLoginFlow() {
        getPage().navigate(ConfigReader.getBaseUrl());

        NavigationBar navBar = new NavigationBar(getPage());
        AuthModalPage authModal = navBar.clickLoginRegister();

        authModal.login("user@example.com", "password123");
        getPage().waitForTimeout(500);

        Assert.assertTrue(navBar.isLoggedIn(), "User badge should be visible on navbar after login");
        Assert.assertFalse(authModal.isModalVisible(), "Modal should automatically close on successful login");
    }
}
