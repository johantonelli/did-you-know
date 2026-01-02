import { test, expect } from '@playwright/test';

test.describe('Wikipedia API Integration Tests @integration', () => {

  test.setTimeout(60000);

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for initial content with generous timeout for real API
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 30000 });
  });

  test('loads random article on initial page load', async ({ page }) => {
    // Content should already be loaded from beforeEach
    await expect(page.locator('#fact-title')).not.toBeEmpty();
    await expect(page.locator('#fact-text')).not.toBeEmpty();
  });

  test('reload button loads new article', async ({ page }) => {
    await page.click('#reload-btn');

    // Wait for loading to finish
    await page.waitForSelector('#loading', { state: 'hidden', timeout: 30000 });
    await expect(page.locator('#fact-title')).not.toBeEmpty();
  });

  test('category navigation updates UI and URL', async ({ page }) => {
    await page.click('#category-links a[href="#physics"]');

    await expect(page.locator('#current-category')).toContainText('Physics');
    expect(page.url()).toContain('#physics');

    // Wait for category article to load (can be slow)
    await page.waitForSelector('#loading', { state: 'hidden', timeout: 30000 });
    await expect(page.locator('#fact-title')).not.toBeEmpty();
  });

  test('category search shows results', async ({ page }) => {
    await page.fill('#category-search-input', 'history');

    // Wait for search results dropdown
    const results = page.locator('#category-search-results');
    await expect(results).toBeVisible({ timeout: 20000 });

    // Should have at least one result containing our search term
    await expect(results.locator('.search-result-item').first()).toContainText(/history/i);
  });

  test('clicking search result navigates to category', async ({ page }) => {
    await page.fill('#category-search-input', 'science');
    await page.waitForSelector('#category-search-results', { state: 'visible', timeout: 20000 });

    // Click first result
    await page.locator('#category-search-results .search-result-item').first().click();

    // Should update category and load content
    await page.waitForSelector('#loading', { state: 'hidden', timeout: 30000 });
    await expect(page.locator('#current-category')).not.toHaveText('All Categories');
  });

  test('article includes Wikipedia link', async ({ page }) => {
    const title = await page.locator('#fact-title').textContent();

    // Only check if we got a real article (not error state)
    if (title && !title.includes('Oops')) {
      await expect(page.locator('#fact-link')).toBeVisible();
      await expect(page.locator('#fact-link')).toHaveAttribute('href', /wikipedia\.org/);
    }
  });

  test('loading indicator hidden after content loads', async ({ page }) => {
    // Content loaded in beforeEach, loading should be hidden
    await expect(page.locator('#loading')).toBeHidden();
  });

  test('entropy mode activates from footer', async ({ page }) => {
    await page.click('.footer');

    await expect(page.locator('#current-category')).toContainText('Entropy mode active');
    await expect(page.locator('#entropy-overlay')).toBeVisible();
  });

  test('entropy mode shows progress bar', async ({ page }) => {
    await page.click('.footer');
    await expect(page.locator('#entropy-overlay')).toBeVisible();

    // Move mouse to collect entropy
    for (let i = 0; i < 30; i++) {
      await page.mouse.move(100 + i * 15, 100 + i * 10);
    }

    await expect(page.locator('#entropy-bar')).toBeVisible();
  });
});
