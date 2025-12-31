import { test, expect } from '@playwright/test';

test.describe('Wikipedia API Integration Tests @integration', () => {

  // Increase timeout for real API calls
  test.setTimeout(30000);

  test.beforeEach(async ({ page }) => {
    // Don't mock API calls - use real Wikipedia API
    await page.goto('/');
  });

  test('loads random article on initial page load', async ({ page }) => {
    // Wait for fact container to be visible
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // Check that we have a title and text
    const title = await page.locator('#fact-title').textContent();
    const text = await page.locator('#fact-text').textContent();

    expect(title).toBeTruthy();
    expect(title).not.toBe('');
    expect(text).toBeTruthy();
    expect(text!.length).toBeGreaterThan(10);
  });

  test('loads new article when reload button clicked', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // Click reload button
    await page.click('#reload-btn');

    // Wait for loading to complete
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    const newTitle = await page.locator('#fact-title').textContent();
    expect(newTitle).toBeTruthy();
  });

  test('category navigation updates UI', async ({ page }) => {
    // Click on Physics category link
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });
    await page.click('#category-links a[href="#physics"]');

    // Category indicator should update
    await expect(page.locator('#current-category')).toContainText('Physics', { timeout: 5000 });

    // URL should contain the hash
    expect(page.url()).toContain('#physics');

    // Eventually should show either an article or error (categories can take time)
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 30000 });
    const title = await page.locator('#fact-title').textContent();
    expect(title).toBeTruthy();
  });

  test('category search returns results from Wikipedia', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 10000 });

    // Type in search box
    await page.fill('#category-search-input', 'history');

    // Wait for results to appear (debounced, Wikipedia API can be slow)
    await page.waitForSelector('#category-search-results', { state: 'visible', timeout: 15000 });

    // Should have at least one result
    const results = await page.locator('#category-search-results .search-result-item').count();
    expect(results).toBeGreaterThan(0);

    // Results should contain "history" related text
    const firstResult = await page.locator('#category-search-results .search-result-item').first().textContent();
    expect(firstResult?.toLowerCase()).toContain('history');
  });

  test('clicking search result navigates to that category', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 10000 });

    // Search for a category
    await page.fill('#category-search-input', 'science');
    await page.waitForSelector('#category-search-results', { state: 'visible', timeout: 15000 });

    // Get the first result text
    const firstResultText = await page.locator('#category-search-results .search-result-item').first().textContent();

    // Click the first result
    await page.locator('#category-search-results .search-result-item').first().click();

    // Wait for new article to load
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 10000 });

    // Category should be updated
    const currentCategory = await page.locator('#current-category').textContent();
    expect(currentCategory).toBeTruthy();
  });

  test('article includes Wikipedia link', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // For random articles (all categories), we should get a valid article with a link
    const title = await page.locator('#fact-title').textContent();

    // If we got an actual article (not error), verify link exists
    if (title && title !== 'Oops!') {
      const wikiLink = page.locator('#fact-link');
      await expect(wikiLink).toBeVisible({ timeout: 5000 });

      const href = await wikiLink.getAttribute('href');
      expect(href).toContain('wikipedia.org');
    }
  });

  test('loading indicator hidden after content loads', async ({ page }) => {
    // Navigate to base URL
    await page.goto('/');

    // Wait for content to load
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // After load, loading indicator should be hidden
    await expect(page.locator('#loading')).toBeHidden();
  });

  test('reload button triggers new article fetch', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // Get initial title
    const initialTitle = await page.locator('#fact-title').textContent();

    // Click reload
    await page.click('#reload-btn');

    // Wait for content to load again
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // Should have a title (may or may not be different)
    const newTitle = await page.locator('#fact-title').textContent();
    expect(newTitle).toBeTruthy();
  });

  test('entropy mode activates from footer link', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // Click the entropy footer link
    await page.click('.footer');

    // Wait for entropy mode elements
    await expect(page.locator('#current-category')).toContainText('Entropy mode active', { timeout: 5000 });
    await expect(page.locator('#entropy-overlay')).toBeVisible({ timeout: 5000 });
  });

  test('entropy mode collects mouse movement', async ({ page }) => {
    await page.waitForSelector('#fact-container', { state: 'visible', timeout: 15000 });

    // Click footer to activate entropy mode
    await page.click('.footer');

    // Wait for entropy overlay
    await expect(page.locator('#entropy-overlay')).toBeVisible({ timeout: 5000 });

    // Simulate mouse movements
    for (let i = 0; i < 50; i++) {
      await page.mouse.move(100 + i * 10, 100 + i * 5);
    }

    // Wait a bit for entropy to build
    await page.waitForTimeout(500);

    // Check progress bar exists
    const progressBar = page.locator('#entropy-bar');
    await expect(progressBar).toBeVisible();
  });
});
