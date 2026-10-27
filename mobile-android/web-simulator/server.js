const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3300;
const PUBLIC_DIR = __dirname;

const MIME_TYPES = {
  '.html': 'text/html',
  '.css': 'text/css',
  '.js': 'text/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

// In-Memory Database for Threat Rules and Pre-Warmed VirusTotal Cache
let threatRules = [
  { id: 'blk-1', indicatorType: 'URL', indicatorValue: 'secure-login-verify.co', listType: 'BLACKLIST', threatCategory: 'PHISHING', source: 'USER_REPORT', confidenceScore: 100, subtitle: 'Reported by you · Today' },
  { id: 'blk-2', indicatorType: 'URL', indicatorValue: 'paypa1-support.net', listType: 'BLACKLIST', threatCategory: 'PHISHING', source: 'USER_REPORT', confidenceScore: 100, subtitle: 'Reported by you · 25 Oct' },
  { id: 'blk-3', indicatorType: 'PHONE', indicatorValue: '+91 98200 11234', listType: 'BLACKLIST', threatCategory: 'SPAM', source: 'USER_REPORT', confidenceScore: 90, subtitle: 'Reported by you · 24 Oct' },
  { id: 'blk-4', indicatorType: 'URL', indicatorValue: 'free-recharge-offer.in', listType: 'BLACKLIST', threatCategory: 'SPAM', source: 'ADMIN_POLICY', confidenceScore: 100, subtitle: 'Added by your organization · 20 Oct' },
  { id: 'tst-1', indicatorType: 'URL', indicatorValue: 'bank.example.in', listType: 'WHITELIST', threatCategory: 'SAFE', source: 'USER_BYPASS', confidenceScore: 100, subtitle: 'Trusted by you · 26 Oct' },
  { id: 'tst-2', indicatorType: 'EMAIL', indicatorValue: 'alerts@hdfcbank.net', listType: 'WHITELIST', threatCategory: 'SAFE', source: 'USER_BYPASS', confidenceScore: 100, subtitle: 'Trusted by you · 22 Oct' },
  { id: 'tst-3', indicatorType: 'PHONE', indicatorValue: '+91 22 6160 6161', listType: 'WHITELIST', threatCategory: 'SAFE', source: 'USER_BYPASS', confidenceScore: 100, subtitle: 'Trusted by you · 18 Oct' },
  { id: 'tst-4', indicatorType: 'URL', indicatorValue: 'intranet.example.com', listType: 'WHITELIST', threatCategory: 'SAFE', source: 'USER_BYPASS', confidenceScore: 100, subtitle: 'Trusted by you · ', expiresAt: new Date(Date.now() + 15 * 60000).toISOString() }
];

// Helper to parse JSON body
function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => { body += chunk.toString(); });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer(async (req, res) => {
  const urlParts = req.url.split('?');
  const pathname = urlParts[0];

  // CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    res.end();
    return;
  }

  // --- API ROUTING (/api/v1/...) ---

  // POST /api/v1/auth/login
  if (pathname === '/api/v1/auth/login' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      sendJson(res, 200, {
        token: 'obsidian-token-' + Buffer.from(body.email || 'user').toString('hex'),
        userEmail: body.email || 'priya@example.com',
        role: 'EMPLOYEE'
      });
    } catch (e) {
      sendJson(res, 400, { error: 'Invalid JSON request' });
    }
    return;
  }

  // POST /api/v1/scan/unified
  if (pathname === '/api/v1/scan/unified' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const urls = body.urls || [];
      const primaryUrl = urls[0] || '';
      const domain = primaryUrl.replace(/^https?:\/\//i, '').split('/')[0].toLowerCase();
      const sender = (body.sender || '').toLowerCase();

      // Rule Precedence:
      // 1. Whitelist match
      const whitelisted = threatRules.find(r => r.listType === 'WHITELIST' && (r.indicatorValue.toLowerCase() === domain || r.indicatorValue.toLowerCase() === primaryUrl));
      if (whitelisted) {
        sendJson(res, 200, {
          verdict: 'CLEAN',
          category: 'SAFE',
          vtDetections: 0,
          indicatorChecked: primaryUrl,
          details: 'Allowed by device whitelist policy'
        });
        return;
      }

      // 2. Blacklist match
      const blacklisted = threatRules.find(r => r.listType === 'BLACKLIST' && (r.indicatorValue.toLowerCase() === domain || r.indicatorValue.toLowerCase() === primaryUrl || r.indicatorValue.toLowerCase() === sender));
      if (blacklisted) {
        sendJson(res, 200, {
          verdict: 'MALICIOUS',
          category: blacklisted.threatCategory || 'PHISHING',
          vtDetections: 14,
          indicatorChecked: primaryUrl,
          details: 'Matched active blacklist rule'
        });
        return;
      }

      // 3. VirusTotal & Heuristics simulation
      if (domain.includes('secure-login') || domain.includes('paypa1') || domain.includes('kyc-update') || domain.includes('free-recharge')) {
        sendJson(res, 200, {
          verdict: 'MALICIOUS',
          category: 'PHISHING',
          vtDetections: 14,
          indicatorChecked: primaryUrl,
          details: 'Flagged by 14 security vendors on VirusTotal v3'
        });
      } else if (domain.includes('bit.ly') || domain.includes('tinyurl') || domain.includes('is.gd')) {
        sendJson(res, 200, {
          verdict: 'SUSPICIOUS',
          category: 'SPAM',
          vtDetections: 2,
          indicatorChecked: primaryUrl,
          details: 'Shortened link hiding destination'
        });
      } else if (domain.includes('track-parcel-info.net')) {
        sendJson(res, 200, {
          verdict: 'PENDING',
          category: 'SAFE',
          vtDetections: 0,
          indicatorChecked: primaryUrl,
          details: 'Awaiting VirusTotal v3 analysis'
        });
      } else {
        sendJson(res, 200, {
          verdict: 'CLEAN',
          category: 'SAFE',
          vtDetections: 0,
          indicatorChecked: primaryUrl,
          details: 'Verified clean by threat intelligence cache'
        });
      }
    } catch (e) {
      sendJson(res, 400, { error: 'Invalid scan request' });
    }
    return;
  }

  // GET /api/v1/rules
  if (pathname === '/api/v1/rules' && req.method === 'GET') {
    sendJson(res, 200, threatRules);
    return;
  }

  // POST /api/v1/rules/report
  if (pathname === '/api/v1/rules/report' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const val = (body.indicatorValue || '').trim();
      const newRule = {
        id: 'blk-' + Date.now(),
        indicatorType: body.indicatorType || 'URL',
        indicatorValue: val,
        listType: 'BLACKLIST',
        threatCategory: 'PHISHING',
        source: 'USER_REPORT',
        confidenceScore: 100,
        subtitle: 'Reported by you · Today'
      };
      // Remove from whitelist if exists
      threatRules = threatRules.filter(r => r.indicatorValue.toLowerCase() !== val.toLowerCase());
      threatRules.unshift(newRule);
      sendJson(res, 201, newRule);
    } catch (e) {
      sendJson(res, 400, { error: 'Invalid report request' });
    }
    return;
  }

  // POST /api/v1/rules/bypass
  if (pathname === '/api/v1/rules/bypass' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const val = (body.indicatorValue || '').trim();
      const ttlMinutes = body.ttlMinutes || 15;
      const newRule = {
        id: 'tst-' + Date.now(),
        indicatorType: body.indicatorType || 'URL',
        indicatorValue: val,
        listType: 'WHITELIST',
        threatCategory: 'SAFE',
        source: 'USER_BYPASS',
        confidenceScore: 100,
        subtitle: 'Trusted by you · Today',
        expiresAt: new Date(Date.now() + ttlMinutes * 60000).toISOString()
      };
      // Remove from blacklist if exists
      threatRules = threatRules.filter(r => r.indicatorValue.toLowerCase() !== val.toLowerCase());
      threatRules.unshift(newRule);
      sendJson(res, 201, newRule);
    } catch (e) {
      sendJson(res, 400, { error: 'Invalid bypass request' });
    }
    return;
  }

  // DELETE /api/v1/rules/:id
  if (pathname.startsWith('/api/v1/rules/') && req.method === 'DELETE') {
    const id = pathname.replace('/api/v1/rules/', '');
    threatRules = threatRules.filter(r => r.id !== id);
    sendJson(res, 200, { deleted: true, id });
    return;
  }

  // --- STATIC FILE SERVING ---
  let filePath = path.join(PUBLIC_DIR, pathname === '/' ? 'index.html' : pathname);

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      filePath = path.join(PUBLIC_DIR, 'index.html');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(filePath, (error, content) => {
      if (error) {
        res.writeHead(500);
        res.end(`Error loading ${filePath}: ${error.code}`);
      } else {
        res.writeHead(200, {
          'Content-Type': contentType,
          'Cache-Control': 'no-cache'
        });
        res.end(content, 'utf-8');
      }
    });
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Obsidian Mobile Full-Stack Simulator & API running at http://localhost:${PORT}`);
  console.log(`  REST API Endpoints:`);
  console.log(`    POST   /api/v1/auth/login`);
  console.log(`    POST   /api/v1/scan/unified`);
  console.log(`    GET    /api/v1/rules`);
  console.log(`    POST   /api/v1/rules/report`);
  console.log(`    POST   /api/v1/rules/bypass`);
  console.log(`    DELETE /api/v1/rules/:id`);
});
