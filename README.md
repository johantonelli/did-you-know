# Did You Know

_Generated using claude code_

A simple static website built with Kotlin/JS that displays random Wikipedia articles. Each page reload shows a different article with the first 100 words and a link to read more.

## Features

- Fetches random Wikipedia articles using the Wikipedia API
- **Category filtering**: Browse facts by category using URL paths (e.g., `/science`, `/history`)
- Displays the first 100 words with a link to the full article
- Built entirely with Kotlin/JS
- Deployed as a static site on GitHub Pages

## Categories

You can filter random facts by category using the URL path:

- `/` - All categories (completely random)
- `/science` - Science articles
- `/history` - History articles
- `/technology` - Technology articles
- `/animals` - Animal articles
- `/geography` - Geography articles
- `/music` - Music articles
- `/art` - Art articles
- `/sports` - Sports articles

**Custom categories**: You can also use any Wikipedia category by typing it in the URL. For example, `/Physics` will fetch random articles from Wikipedia's "Category:Physics".

**Local file usage**: When opening the HTML file locally (via `file://`), use hash-based URLs instead (e.g., `#science`, `#history`). The category links at the bottom of the page will work automatically in both modes.

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
│   └── main/
│       ├── kotlin/
│       │   └── Main.kt          # Kotlin/JS code for Wikipedia API integration
│       └── resources/
│           └── index.html       # HTML template with embedded CSS
├── build.gradle.kts             # Gradle build configuration
├── settings.gradle.kts          # Gradle settings
└── .github/
    └── workflows/
        └── deploy.yml           # GitHub Actions workflow
```

## How It Works

1. **Kotlin/JS Compilation**: Kotlin code is compiled to JavaScript using the Kotlin/JS compiler
2. **URL-based Routing**: The URL path determines which Wikipedia category to fetch from
3. **Wikipedia API**: Uses Wikipedia's public API to fetch random articles or category members
4. **Dynamic Content**: JavaScript fetches and displays new content on each page load or button click
5. **GitHub Pages**: Serves the static HTML and compiled JavaScript files

## Technologies Used

- **Kotlin/JS**: Main programming language, compiled to JavaScript
- **Wikipedia API**: Source of random article data
- **GitHub Pages**: Free static site hosting
- **GitHub Actions**: Automated build and deployment

## License

MIT License - feel free to use and modify as you wish!
