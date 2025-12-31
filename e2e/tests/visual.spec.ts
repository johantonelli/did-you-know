import { test, expect, Page } from '@playwright/test';

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
    categorymembers: [{ pageid: 12345, ns: 0, title: 'Test Article' }]
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
  query: { categorymembers: [] }
};

// Helper to disable animations for consistent screenshots
async function disableAnimations(page: Page) {
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
        transition-delay: 0s !important;
        scroll-behavior: auto !important;
      }
    `
  });
}

// Helper to wait for page to be visually stable
async function waitForStableState(page: Page) {
  await page.waitForLoadState('load');
  await page.waitForLoadState('networkidle');

  // Wait for fonts
  await page.evaluate(() => document.fonts.ready);

  // Poll until CSS is applied (check header background color is powder blue)
  await page.waitForFunction(() => {
    const header = document.querySelector('.header');
    if (!header) return false;
    const bg = window.getComputedStyle(header).backgroundColor;
    return bg.includes('168') && bg.includes('204') && bg.includes('215');
  }, { timeout: 10000 }).catch(() => {
    // Continue even if CSS check times out
  });

  // Wait for pending renders
  await page.evaluate(() => new Promise(resolve =>
    requestAnimationFrame(() => requestAnimationFrame(resolve))
  ));

  // Extra stabilization time for CI
  await page.waitForTimeout(process.env.CI ? 500 : 300);
}

// Helper to wait for fact content to be fully loaded
async function waitForFactContent(page: Page) {
  await page.waitForSelector('#fact-container', { state: 'visible' });
  await page.waitForFunction(() => {
    const title = document.getElementById('fact-title');
    const text = document.getElementById('fact-text');
    return title?.textContent?.trim() && text?.textContent?.trim();
  });
}

// Helper for consistent screenshot taking
async function takeScreenshot(page: Page, name: string) {
  await disableAnimations(page);
  await waitForStableState(page);
  await expect(page).toHaveScreenshot(name);
}

test.describe('Visual Regression Tests @visual', () => {

  test.beforeEach(async ({ page }) => {
    await page.route('**/api.php*', async (route) => {
      const url = route.request().url();

      if (url.includes('generator=random')) {
        await route.fulfill({ json: mockArticle });
      } else if (url.includes('list=categorymembers')) {
        await route.fulfill({ json: mockCategoryMembers });
      } else if (url.includes('list=allcategories')) {
        await route.fulfill({ json: mockCategories });
      } else {
        await route.fulfill({ json: mockArticle });
      }
    });
  });

  test('loading state', async ({ page }) => {
    // Delay API response to capture loading state
    await page.route('**/api.php*', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 10000));
      await route.fulfill({ json: mockArticle });
    });

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.header', { state: 'visible' });
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(process.env.CI ? 500 : 300);

    await expect(page.locator('#loading')).toBeVisible();
    await disableAnimations(page);
    await expect(page).toHaveScreenshot('loading-state.png');
  });

  test('fact display - all categories', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await takeScreenshot(page, 'fact-display-all-categories.png');
  });

  // Parameterized category tests
  for (const category of ['physics', 'dogs', 'space']) {
    test(`fact display - ${category} category`, async ({ page }) => {
      await page.goto('/');
      await waitForFactContent(page);

      await page.click(`#category-links a[href="#${category}"]`);
      await expect(page.locator('#current-category')).toContainText(
        category.charAt(0).toUpperCase() + category.slice(1)
      );
      await waitForFactContent(page);

      await takeScreenshot(page, `fact-display-${category}.png`);
    });
  }

  test('long article with scroll', async ({ page }) => {
    await page.route('**/api.php*', async (route) => {
      await route.fulfill({ json: mockLongArticle });
    });

    await page.goto('/');
    await waitForFactContent(page);
    await takeScreenshot(page, 'long-article-scroll.png');
  });

  test('category search dropdown', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await disableAnimations(page);

    await page.fill('#category-search-input', 'hist');
    await page.waitForSelector('#category-search-results', { state: 'visible' });
    await page.waitForFunction(() => {
      const results = document.getElementById('category-search-results');
      return results && results.children.length > 0;
    });

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
    await page.waitForFunction(() => {
      const title = document.getElementById('fact-title');
      return title?.textContent?.includes('Oops');
    });

    await takeScreenshot(page, 'error-no-articles.png');
  });

  test('entropy mode active', async ({ page }) => {
    // Remove mocked routes - entropy mode needs real API
    await page.unroute('**/api.php*');

    await page.goto('/');
    await page.waitForSelector('#fact-container', { state: 'visible' });

    await page.click('.footer');
    await expect(page.locator('#current-category')).toContainText('Entropy mode active');
    await expect(page.locator('#entropy-overlay')).toBeVisible();

    await takeScreenshot(page, 'entropy-mode-overlay.png');
  });

  test('category links hover state', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await disableAnimations(page);
    await waitForStableState(page);

    await page.hover('#category-links a:nth-child(2)');
    await page.waitForTimeout(200);

    await expect(page).toHaveScreenshot('category-link-hover.png');
  });

  test('footer hover state', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);
    await disableAnimations(page);
    await waitForStableState(page);

    await page.hover('.footer');
    await page.waitForTimeout(200);

    await expect(page).toHaveScreenshot('footer-hover.png');
  });

  test('active category highlighting', async ({ page }) => {
    await page.goto('/');
    await waitForFactContent(page);

    await page.click('#category-links a[href="#space"]');
    await expect(page.locator('#current-category')).toContainText('Space');
    await waitForFactContent(page);

    const spaceLink = page.locator('#category-links a', { hasText: 'Space' });
    await expect(spaceLink).toHaveClass(/active/);

    await takeScreenshot(page, 'active-category-space.png');
  });
});
