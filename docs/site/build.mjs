// Builds the public website (privacy policy, terms, account deletion, share-link page w/, app-ads.txt, assetlinks.json)
// from docs/site/src + docs/site/site.config.json into docs/site/dist.
//
//   node docs/site/build.mjs              → build only (preview dist/ locally)
//   node docs/site/build.mjs --publish    → build, then push dist/ to the GitHub Pages repo
//
// Published at https://raunakpatil.github.io/wallpaper-app-site/ (repo raunakpatil/wallpaper-app-site, public).
// NEVER point this at raunakpatil/raunakpatil.github.io: that is Raunak's personal portfolio.
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const src = join(here, 'src');
const dist = join(here, 'dist');
const cfg = JSON.parse(readFileSync(join(here, 'site.config.json'), 'utf8'));
const PAGES_REPO = 'https://github.com/raunakpatil/wallpaper-app-site.git';
const SITE_URL = 'https://raunakpatil.github.io/wallpaper-app-site/';
const MARKER = 'Generated from `docs/site` in the private app repo.';

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const todo = (label) => `<mark class="todo">[${label}]</mark>`;

const appName = cfg.APP_NAME ? esc(cfg.APP_NAME) : todo('App Name');
const appNamePlain = cfg.APP_NAME ? esc(cfg.APP_NAME) : 'App Name';
const email = cfg.CONTACT_EMAIL
  ? `<a href="mailto:${esc(cfg.CONTACT_EMAIL)}">${esc(cfg.CONTACT_EMAIL)}</a>`
  : todo('contact email');

const header = `<header class="site"><nav>
  <a class="brand" href="./">${appName}</a>
  <a href="privacy.html">Privacy</a><a href="terms.html">Terms</a><a href="delete-account.html">Delete account</a>
</nav></header>`;
const footer = `<footer>© ${new Date().getFullYear()} ${esc(cfg.PUBLISHERS)} · <a href="privacy.html">Privacy</a> · <a href="terms.html">Terms</a> · <a href="delete-account.html">Delete account</a></footer>`;
const playButton = cfg.PLAY_STORE_URL
  ? `<p><a class="btn" href="${esc(cfg.PLAY_STORE_URL)}">Get it on Google Play</a></p>`
  : `<p class="meta">Coming soon to Google Play.</p>`;

/** `root` = path back to the site root ('' for top-level pages, '../' for sub-pages like w/). */
function render(html, root = '') {
  return html
    // Plain-text contexts (<title>, meta) can't contain markup.
    .replace(/(<title>[^<]*<\/title>|<meta [^>]*>)/g, (m) => m.replace(/\{\{APP_NAME\}\}/g, appNamePlain))
    .replace('</head>', `  <link rel="icon" type="image/png" href="${root}favicon.png">\n</head>`)
    .replaceAll('{{HEADER}}', header)
    .replaceAll('{{FOOTER}}', footer)
    .replaceAll('{{PLAY_BUTTON}}', playButton)
    .replaceAll('{{APP_NAME}}', appName)
    .replaceAll('{{CONTACT_EMAIL}}', email)
    .replaceAll('{{PLAY_STORE_URL}}', esc(cfg.PLAY_STORE_URL || ''))
    .replaceAll('{{ANDROID_PACKAGE}}', esc(cfg.ANDROID_PACKAGE))
    .replaceAll('{{PUBLISHERS}}', esc(cfg.PUBLISHERS))
    .replaceAll('{{COUNTRY}}', esc(cfg.COUNTRY))
    .replaceAll('{{EFFECTIVE_DATE}}', esc(cfg.EFFECTIVE_DATE));
}

rmSync(dist, { recursive: true, force: true });
mkdirSync(join(dist, '.well-known'), { recursive: true });

for (const f of readdirSync(src, { withFileTypes: true })) {
  if (f.isDirectory()) {
    // Sub-pages (e.g. w/ = share links, CONTRACTS §7): rendered the same way, they link ../style.css themselves.
    mkdirSync(join(dist, f.name), { recursive: true });
    for (const g of readdirSync(join(src, f.name))) {
      if (g.endsWith('.html')) writeFileSync(join(dist, f.name, g), render(readFileSync(join(src, f.name, g), 'utf8'), '../'));
    }
  } else if (f.name.endsWith('.html')) writeFileSync(join(dist, f.name), render(readFileSync(join(src, f.name), 'utf8')));
  else if (f.name === 'style.css' || /\.(png|ico|webp)$/.test(f.name)) cpSync(join(src, f.name), join(dist, f.name));
}

// app-ads.txt must sit at the root of the "developer website" listed on Google Play.
const pub = (cfg.ADMOB_PUBLISHER_ID || '').trim();
if (pub && !/^pub-\d{16}$/.test(pub)) throw new Error(`ADMOB_PUBLISHER_ID must look like pub-1234567890123456, got "${pub}"`);
writeFileSync(join(dist, 'app-ads.txt'), pub
  ? `google.com, ${pub}, DIRECT, f08c47fec0942fa0\n`
  : '# AdMob publisher id not set yet. Add ADMOB_PUBLISHER_ID to docs/site/site.config.json before launch.\n');

// Digital Asset Links so https links open the app (B11). Needs the Play app-signing SHA-256.
const fps = cfg.SHA256_CERT_FINGERPRINTS || [];
writeFileSync(join(dist, '.well-known', 'assetlinks.json'), JSON.stringify(fps.length ? [{
  relation: ['delegate_permission/common.handle_all_urls'],
  target: { namespace: 'android_app', package_name: cfg.ANDROID_PACKAGE, sha256_cert_fingerprints: fps },
}] : [], null, 2) + '\n');

// Jekyll would hide .well-known; serve files as-is.
writeFileSync(join(dist, '.nojekyll'), '');
writeFileSync(join(dist, 'README.md'),
  '# raunakpatil.github.io\n\nGenerated from `docs/site` in the private app repo. Do not edit here; edit the source and run `node docs/site/build.mjs --publish`.\n');

const missing = [!cfg.APP_NAME && 'APP_NAME', !cfg.CONTACT_EMAIL && 'CONTACT_EMAIL', !pub && 'ADMOB_PUBLISHER_ID'].filter(Boolean);
console.log(`Built ${readdirSync(dist).length} entries into ${dist}`);
if (missing.length) console.log(`Placeholders still shown for: ${missing.join(', ')} (fill in site.config.json before launch)`);

if (process.argv.includes('--publish')) {
  const work = join(here, '.publish');
  rmSync(work, { recursive: true, force: true });
  const git = (...args) => execFileSync('git', args, { cwd: work, stdio: 'pipe' }).toString().trim();
  execFileSync('git', ['clone', '--depth', '1', PAGES_REPO, work], { stdio: 'pipe' });
  // Safety: only ever overwrite a repo that is empty or was created by this script.
  const existing = readdirSync(work).filter((f) => f !== '.git');
  const readme = join(work, 'README.md');
  if (existing.length && !(existsSync(readme) && readFileSync(readme, 'utf8').includes(MARKER))) {
    rmSync(work, { recursive: true, force: true });
    throw new Error(`Refusing to publish: ${PAGES_REPO} contains files not generated by this script (${existing.slice(0, 5).join(', ')}).`);
  }
  for (const f of existing) rmSync(join(work, f), { recursive: true, force: true });
  cpSync(dist, work, { recursive: true });
  git('add', '-A');
  if (!git('status', '--porcelain')) {
    console.log('Nothing changed; site already up to date.');
  } else {
    git('commit', '-m', `Update site (${new Date().toISOString().slice(0, 10)})`);
    git('push', 'origin', 'HEAD');
    console.log(`Published to ${SITE_URL} (GitHub Pages updates in ~1 minute).`);
  }
  rmSync(work, { recursive: true, force: true });
  if (existsSync(work)) console.log(`Note: couldn't remove ${work}`);
}
