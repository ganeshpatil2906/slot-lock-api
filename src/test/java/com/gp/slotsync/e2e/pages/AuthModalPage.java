package com.gp.slotsync.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for the Authentication Modal (Login and Register).
 */
public class AuthModalPage extends BasePage {

    private final Locator modalOverlay;
    private final Locator modalTitle;
    private final Locator modalSubtitle;
    private final Locator toggleLink;
    private final Locator nameField;
    private final Locator emailField;
    private final Locator passwordField;
    private final Locator roleSelect;
    private final Locator submitButton;
    private final Locator closeButton;

    public AuthModalPage(Page page) {
        super(page);
        this.modalOverlay = page.locator("#authModal");
        this.modalTitle = page.locator("#authModalTitle");
        this.modalSubtitle = page.locator("#authModalSubtitle");
        this.toggleLink = page.locator("#authToggleLink");
        this.nameField = page.locator("#authName");
        this.emailField = page.locator("#authEmail");
        this.passwordField = page.locator("#authPassword");
        this.roleSelect = page.locator("#authRole");
        this.submitButton = page.locator("#authSubmitBtn");
        this.closeButton = page.locator("#authModal button:has(i.fa-xmark)");
    }

    public boolean isModalVisible() {
        String classes = modalOverlay.getAttribute("class");
        return classes != null && classes.contains("active");
    }

    public String getModalTitle() {
        return modalTitle.innerText().trim();
    }

    public String getModalSubtitle() {
        return modalSubtitle.innerText().trim();
    }

    public void clickToggleMode() {
        toggleLink.click();
    }

    public boolean isNameFieldVisible() {
        return nameField.isVisible();
    }

    public boolean isRoleFieldVisible() {
        return roleSelect.isVisible();
    }

    public void fillName(String name) {
        nameField.fill(name);
    }

    public void fillEmail(String email) {
        emailField.fill(email);
    }

    public void fillPassword(String password) {
        passwordField.fill(password);
    }

    public void selectRole(String role) {
        roleSelect.selectOption(role);
    }

    public void clickSubmit() {
        submitButton.click();
    }

    public void closeModal() {
        closeButton.click();
    }

    public void login(String email, String password) {
        fillEmail(email);
        fillPassword(password);
        clickSubmit();
    }

    public void register(String name, String email, String password, String role) {
        if (!isNameFieldVisible()) {
            clickToggleMode();
        }
        fillName(name);
        fillEmail(email);
        fillPassword(password);
        if (role != null && !role.isEmpty()) {
            selectRole(role);
        }
        clickSubmit();
    }
}
