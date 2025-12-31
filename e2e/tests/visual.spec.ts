import { test, expect, Page } from '@playwright/test';

// Helper to wait for page to be visually stable
async function waitForStableState(page: Page) {
  // Wait for load event which ensures CSS is loaded
  await page.waitForLoadState('load');

  // Wait for network to be idle
  await page.waitForLoadState('networkidle');

  // Wait for fonts to load
  await page.evaluate(() => document.fonts.ready);

  // Poll until CSS is applied (check header background color)
  // The header should have --powder-blue (#A8CCD7 = rgb(168, 204, 215)) background
  let cssApplied = false;
  for (let i = 0; i < 20; i++) {
    cssApplied = await page.evaluate(() => {
      const header = document.querySelector('.header');
      if (!header) return false;
      const style = window.getComputedStyle(header);
      const bg = style.backgroundColor;
      // Check for powder blue color
      return bg.includes('168') && bg.includes('204') && bg.includes('215');
    });
    if (cssApplied) break;
    await page.waitForTimeout(100);
  }

  // Wait for any pending renders
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));

  // Extra delay for stability
  await page.waitForTimeout(300);
}

// Helper to disable animations for consistent screenshots
async function disableAnimations(page: Page) {
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
        transition-delay: 0s !important;
      }
    `
  });
}

// Helper to wait for fact content to be fully loaded
async function waitForFactContent(page: Page) {
  // Wait for fact container to be visible
  await page.waitForSelector('#fact-container', { state: 'visible', timeout: 10000 });

  // Wait for title to have actual content (not empty)
  await page.waitForFunction(() => {
    const title = document.getElementById('fact-title');
    return title && title.textContent && title.textContent.trim().length > 0;
  }, { timeout: 10000 });

  // Wait for text to have actual content
  await page.waitForFunction(() => {
    const text = document.getElementById('fact-text');
    return text && text.textContent && text.textContent.trim().length > 0;
  }, { timeout: 10000 });
}

// Mock data for consistent visual testing
const mockArticle = {
  batchcomplete: '',
  query: {
    pages: {
      '12345': {
        pageid: 12345,
        ns: 0,
        title: 'Test Article',
        extract: 'This is a test article extract that contains enough text to demonstrate the scrolling functionality. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.'
      }
    }
  }
};

const mockLongArticle = {
  batchcomplete: '',
  query: {
    pages: {
      '12346': {
        pageid: 12346,
        ns: 0,
        title: 'Long Test Article',
        extract: `This is a very long test article that will test the scrolling functionality.

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.

Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo.

Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt.`
      }
    }
  }
};

const mockCategoryMembers = {
  batchcomplete: '',
  query: {
    categorymembers: [
      { pageid: 12345, ns: 0, title: 'Test Article' }
    ]
  }
};

const mockCategories = {
  batchcomplete: '',
  query: {
    allcategories: [
      { '*': 'History' },
      { '*': 'Historical events' },
      { '*': 'History by period' }
    ]
  }
};

const mockEmptyCategory = {
  batchcomplete: '',
  query: {
    categorymembers: []
  }
};

test.describe('Visual Regression Tests @visual', () => {

  test.beforeEach(async ({ page }) => {
    // Set up route handlers before navigating
    await page.route('**/api.php*', async (route) => {
      const url = route.request().url();

      if (url.includes('generator=random')) {
        await route.fulfill({ json: mockArticle });
      } else if (url.includes('list=categorymembers')) {
        await route.fulfill({ json: mockCategoryMembers });
      } else if (url.includes('list=allcategories')) {
        await route.fulfill({ json: mockCategories });
      } else if (url.includes('pageids=')) {
        await route.fulfill({ json: mockArticle });
      } else {
        await route.fulfill({ json: mockArticle });
      }
    });
  });

  // Helper for consistent screenshot taking (viewport only, not full page)
  async function takeStableScreenshot(page: Page, name: string, waitForContent = true) {
    if (waitForContent) {
      await waitForFactContent(page);
    }
    await disableAnimations(page);
    await waitForStableState(page);
    // Use viewport screenshot for consistent dimensions
    await expect(page).toHaveScreenshot(name);
  }

  test('loading state', async ({ page }) => {
    // Delay the API response to capture loading state
    await page.route('**/api.php*', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 10000));
      await route.fulfill({ json: mockArticle });
    });

    await page.goto('/', { waitUntil: 'domcontentloaded' });

    // Wait briefly for CSS to be applied
    await page.waitForSelector('.header', { state: 'visible' });
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(300);

    // Capture loading state before API responds
    await expect(page.locator('#loading')).toBeVisible();
    await disableAnimations(page);
    await expect(page).toHaveScreenshot('loading-state.png');
  });

  test('fact display - all categories', async ({ page }) => {
    await page.goto('/');

    await takeStableScreenshot(page, 'fact-display-all-categories.png');
  });

  test('fact display - physics category', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await waitForStableState(page);

    // Click physics category link
    await page.click('#category-links a[href="#physics"]');
    await expect(page.locator('#current-category')).toContainText('Physics');

    // Wait for new content to load after category change
    await waitForFactContent(page);

    await takeStableScreenshot(page, 'fact-display-physics.png');
  });

  test('fact display - dogs category', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await waitForStableState(page);

    // Click dogs category link
    await page.click('#category-links a[href="#dogs"]');
    await expect(page.locator('#current-category')).toContainText('Dogs');

    // Wait for new content to load after category change
    await waitForFactContent(page);

    await takeStableScreenshot(page, 'fact-display-dogs.png');
  });

  test('long article with scroll', async ({ page }) => {
    await page.route('**/api.php*', async (route) => {
      await route.fulfill({ json: mockLongArticle });
    });

    await page.goto('/');

    await takeStableScreenshot(page, 'long-article-scroll.png');
  });

  test('category search dropdown', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);

    // Disable animations before interacting
    await disableAnimations(page);

    // Type in search box
    await page.fill('#category-search-input', 'hist');
    await page.waitForSelector('#category-search-results', { state: 'visible' });

    // Wait for dropdown to have content
    await page.waitForFunction(() => {
      const results = document.getElementById('category-search-results');
      return results && results.children.length > 0;
    }, { timeout: 10000 });

    await waitForStableState(page);
    await expect(page).toHaveScreenshot('category-search-dropdown.png');
  });

  test('error state - no articles', async ({ page }) => {
    await page.route('**/api.php*', async (route) => {
      const url = route.request().url();
      if (url.includes('list=categorymembers')) {
        await route.fulfill({ json: mockEmptyCategory });
      } else {
        await route.fulfill({ json: mockArticle });
      }
    });

    await page.goto('/#physics');

    // Wait for error content specifically
    await page.waitForFunction(() => {
      const title = document.getElementById('fact-title');
      return title && title.textContent && title.textContent.includes('Oops');
    }, { timeout: 10000 });

    await takeStableScreenshot(page, 'error-no-articles.png');
  });

  // Note: Entropy mode visual test skipped because the mocked API route handler
  // interferes with entropy mode activation. Entropy mode is fully tested in
  // integration.spec.ts without mocking.
  test.skip('entropy mode active', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('#fact-container', { state: 'visible' });

    // Click footer to activate entropy mode
    await page.click('.footer');

    // Check that entropy mode text is shown
    await expect(page.locator('#current-category')).toContainText('Entropy mode active');

    // Wait for entropy overlay to appear (may take a moment)
    await expect(page.locator('#entropy-overlay')).toBeVisible({ timeout: 10000 });

    // Wait for any animations to settle
    await page.waitForTimeout(500);

    await expect(page).toHaveScreenshot('entropy-mode-overlay.png');
  });

  test('category links hover state', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);

    // Disable animations first
    await disableAnimations(page);
    await waitForStableState(page);

    // Hover over a category link
    await page.hover('#category-links a:nth-child(2)');

    // Brief wait for hover style to apply
    await page.waitForTimeout(100);

    await expect(page).toHaveScreenshot('category-link-hover.png');
  });

  test('footer hover state', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);

    // Disable animations first
    await disableAnimations(page);
    await waitForStableState(page);

    // Hover over footer
    await page.hover('.footer');

    // Brief wait for hover style to apply
    await page.waitForTimeout(100);

    await expect(page).toHaveScreenshot('footer-hover.png');
  });

  test('button container visible after load', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);

    await expect(page.locator('.button-container')).toBeVisible();
    await expect(page.locator('#reload-btn')).toBeVisible();
    await expect(page.locator('#img-btn')).toBeVisible();
  });

  test('active category highlighting', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await waitForStableState(page);

    // Click Space category instead of navigating via hash
    await page.click('#category-links a[href="#space"]');
    await expect(page.locator('#current-category')).toContainText('Space');

    // Wait for new content to load after category change
    await waitForFactContent(page);

    // Check that Space category is active
    const spaceLink = page.locator('#category-links a', { hasText: 'Space' });
    await expect(spaceLink).toHaveClass(/active/);

    await takeStableScreenshot(page, 'active-category-space.png');
  });
});
