# Did You Know

_Generated using claude code_

A simple static website built with Kotlin/JS that displays random Wikipedia articles. Each page reload shows a different article with the first 100 words and a link to read more.

## Features

- Fetches random Wikipedia articles using the Wikipedia API
- **Category filtering**: Browse facts by category (Physics, Dogs, Space, Music)
- **Category search**: Search for any Wikipedia category using the search box
- **Entropy mode**: Generate random articles using mouse/touch movement entropy
- Displays article introductions with a link to the full article
- Built entirely with Kotlin/JS
- Deployed as a static site on GitHub Pages

## Categories

You can filter random facts by category using the URL hash:

- `#` or no hash - All categories (completely random)
- `#physics` - Physics articles
- `#dogs` - Dog articles
- `#space` - Space and astronomy articles
- `#music` - Music articles
- `#~entropy` - **Special mode**: Generates a random article based on your mouse/touch movements

**Custom categories**: Use the search box at the top of the page to find any Wikipedia category. Results appear as you type, and clicking a result will filter articles to that category.

### Entropy Mode

The Entropy category is a special interactive mode that uses your cursor or touch movements to generate randomness. Move your cursor or touch the screen for 5 seconds, and your movements will be converted into an "entropy number" that determines which Wikipedia article to fetch. This ensures truly unique, user-influenced randomness!

Category links are available at the bottom of the page for easy navigation.

## Prerequisites

- JDK 17 or higher
- Gradle 8.5+ (or use the wrapper once generated)

## Setup

### 1. Generate Gradle Wrapper (if not present)

If you have Gradle installed:
```bash
gradle wrapper --gradle-version 8.5
```

### 2. Build the Project

```bash
./gradlew build
```

On Windows:
```bash
gradlew.bat build
```

### 3. Run Locally

After building, open `build/dist/js/productionExecutable/index.html` in your browser to test locally.

## Deploying to GitHub Pages

### Option 1: Automatic Deployment with GitHub Actions (Recommended)

1. Create a new repository on GitHub
2. Push this code to the repository:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git push -u origin main
   ```

3. Go to your repository settings on GitHub
4. Navigate to **Pages** (under "Code and automation")
5. Under **Source**, select **GitHub Actions**
6. The workflow will automatically build and deploy on every push to main

Your site will be available at: `https://YOUR_USERNAME.github.io/YOUR_REPO/`

### Option 2: Manual Deployment

1. Build the project: `./gradlew build`
2. Copy contents of `build/dist/js/productionExecutable/` to your repository root or `docs/` folder
3. Commit and push
4. Enable GitHub Pages in repository settings, pointing to the appropriate folder

## Project Structure

```
.
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── Main.kt          # Entry point
│   │   │   ├── Pages.kt         # Wikipedia API integration
│   │   │   ├── SetupUI.kt       # UI setup and category search
│   │   │   └── Entropy.kt       # Entropy mode logic
│   │   └── resources/
│   │       ├── index.html       # HTML template
│   │       └── styles.css       # Stylesheet
│   └── test/
│       └── kotlin/              # Kotlin unit tests
├── e2e/
│   ├── tests/
│   │   ├── visual.spec.ts       # Visual regression tests
│   │   └── integration.spec.ts  # Wikipedia API integration tests
│   ├── playwright.config.ts     # Playwright configuration
│   └── package.json             # E2E test dependencies
├── build.gradle.kts             # Gradle build configuration
├── settings.gradle.kts          # Gradle settings
└── .github/
    └── workflows/
        ├── deploy.yml           # GitHub Pages deployment
        └── test.yml             # Test automation workflow
```

## How It Works

1. **Kotlin/JS Compilation**: Kotlin code is compiled to JavaScript using the Kotlin/JS compiler
2. **Hash-based Routing**: The URL hash determines which Wikipedia category to fetch from
3. **Wikipedia API**: Uses Wikipedia's public API to fetch random articles or category members
4. **Dynamic Content**: JavaScript fetches and displays new content on each page load or button click
5. **GitHub Pages**: Serves the static HTML and compiled JavaScript files

## Testing

The project includes comprehensive testing at multiple levels.

### Unit Tests (Kotlin/JS)

Run the Kotlin unit tests with:
```bash
./gradlew browserTest
```

This runs tests for:
- Text cleanup/extract sanitization
- Category parsing and handling
- Entropy hash generation

### E2E Tests (Playwright)

End-to-end tests use Playwright for visual regression and integration testing.

#### Setup
```bash
cd e2e
npm install
npx playwright install chromium
```

#### Run Visual Tests (with mocked data)
```bash
npm run test:visual
```

#### Run Integration Tests (real Wikipedia API)
```bash
npm run test:integration
```

#### Update Visual Snapshots
```bash
npm run test:update-snapshots
```

#### Run All Tests
```bash
npm test
```

### GitHub Actions

All tests run automatically on push and pull requests via GitHub Actions. The workflow runs:
1. Kotlin unit tests
2. Build verification
3. Visual regression tests (Chromium)
4. Wikipedia API integration tests
5. Cross-browser tests on main branch (Chromium, Firefox, WebKit)

## Technologies Used

- **Kotlin/JS**: Main programming language, compiled to JavaScript
- **Wikipedia API**: Source of random article data
- **Font Awesome**: Icons for buttons and categories
- **GitHub Pages**: Free static site hosting
- **GitHub Actions**: Automated build and deployment
- **Playwright**: E2E and visual regression testing
- **Karma**: Kotlin/JS unit test runner

## License

MIT License - feel free to use and modify as you wish!
