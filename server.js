'use strict';

/**
 * Vorb backend
 * -------------
 * Express server that turns a fresh Cloudflare WARP registration into a
 * ready-to-import WireGuard configuration using the `wgcf` CLI.
 *
 * Requirements:
 *   - Node.js 18+
 *   - wgcf in PATH, or set WGCF_PATH to the binary
 *     https://github.com/ViRb3/wgcf
 *
 * Endpoint:
 *   POST /api/generate  ->  { filename, config }
 *   GET  /api/health    ->  { ok, service, version }
 *
 * Rate limit: 1 request per minute per IP (successful or not).
 */

const path = require('node:path');
const { spawn } = require('node:child_process');
const { mkdtemp, readFile, rm } = require('node:fs/promises');
const { tmpdir } = require('node:os');

const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = Number(process.env.PORT) || 3000;
const WGCF_PATH = process.env.WGCF_PATH || 'wgcf';
const WGCF_TIMEOUT_MS = Number(process.env.WGCF_TIMEOUT_MS) || 35_000;
const CORS_ORIGIN = process.env.CORS_ORIGIN || '*';
const VERSION = require('./package.json').version;

app.disable('x-powered-by');
app.set('trust proxy', 1);

app.use(
  cors({
    origin: CORS_ORIGIN,
    methods: ['GET', 'POST', 'OPTIONS'],
    allowedHeaders: ['Content-Type'],
    maxAge: 86400,
  })
);
app.use(express.json({ limit: '32kb' }));
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  next();
});

const generateLimiter = rateLimit({
  windowMs: 60_000,
  limit: 1,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: {
    error: 'Слишком много запросов. Новый конфиг можно создать через минуту.',
  },
});

/**
 * Run a command and capture its output. Rejects on a non-zero exit code,
 * spawn errors, or timeout.
 *
 * @param {string} command
 * @param {string[]} args
 * @param {string} cwd
 * @returns {Promise<{ stdout: string, stderr: string }>}
 */
function runCommand(command, args, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      env: { ...process.env, NO_COLOR: '1', TERM: 'dumb' },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stdout = '';
    let stderr = '';
    let settled = false;
    let timer;

    const finish = (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (error) reject(error);
      else resolve({ stdout, stderr });
    };

    timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish(new Error(`wgcf не ответил за ${WGCF_TIMEOUT_MS / 1000} секунд`));
    }, WGCF_TIMEOUT_MS);

    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });

    child.on('error', (error) => {
      if (error.code === 'ENOENT') {
        finish(
          new Error(
            'wgcf не найден. Установите утилиту (https://github.com/ViRb3/wgcf) ' +
              'или задайте переменную окружения WGCF_PATH.'
          )
        );
        return;
      }
      finish(error);
    });

    child.on('close', (code) => {
      if (code === 0) {
        finish();
        return;
      }
      const detail = (stderr || stdout || '').trim();
      finish(new Error(detail || `wgcf завершился с кодом ${code}`));
    });
  });
}

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.get('/api/health', (req, res) => {
  res.json({ ok: true, service: 'vorb', version: VERSION });
});

app.post('/api/generate', generateLimiter, async (req, res) => {
  let workDir;

  try {
    workDir = await mkdtemp(path.join(tmpdir(), 'vorb-warp-'));

    // A fresh directory means a fresh WARP identity for every request.
    await runCommand(WGCF_PATH, ['register', '--accept-tos'], workDir);
    await runCommand(WGCF_PATH, ['generate'], workDir);

    const profilePath = path.join(workDir, 'wgcf-profile.conf');
    const rawConfig = await readFile(profilePath, 'utf8');

    const config = [
      '# Vorb • Cloudflare WARP',
      `# Generated: ${new Date().toISOString()}`,
      '# Telegram: https://t.me/VorbVpn',
      '# Keep this file private: it contains your private key.',
      '',
      rawConfig.trim(),
      '',
    ].join('\n');

    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.json({ filename: 'vorb-warp.conf', config });
  } catch (error) {
    console.error('[vorb] generation failed:', error.message);
    res.status(502).json({
      error: 'Не удалось сгенерировать конфиг Cloudflare WARP.',
      details:
        process.env.NODE_ENV === 'production'
          ? undefined
          : error.message,
    });
  } finally {
    if (workDir) {
      await rm(workDir, { recursive: true, force: true }).catch(() => {});
    }
  }
});

app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// eslint-disable-next-line no-unused-vars
app.use((error, req, res, next) => {
  if (error instanceof SyntaxError && 'body' in error) {
    res.status(400).json({ error: 'Некорректный JSON в запросе.' });
    return;
  }
  console.error('[vorb] unhandled error:', error);
  res.status(500).json({ error: 'Внутренняя ошибка сервера.' });
});

if (require.main === module) {
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Vorb backend: http://localhost:${PORT}`);
    console.log(`wgcf binary: ${WGCF_PATH}`);
  });
}

module.exports = app;
