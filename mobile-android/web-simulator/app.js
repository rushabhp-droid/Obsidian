// Obsidian Mobile - Interactive Application Engine

(function () {
  // Initial State
  const state = {
    isUnlocked: false,
    activeTab: 'home', // 'home' | 'messages' | 'rules' | 'settings'
    activeScreen: 'lock', // 'lock' | 'home-setup' | 'home' | 'messages' | 'detail' | 'report-confirmed' | 'rules' | 'settings'
    activeFilter: 'ALL', // 'ALL' | 'SCAM' | 'SUSPICIOUS' | 'CHECKING'
    searchQuery: '',
    selectedMessageId: 'msg-1',
    notificationAccessGranted: true,
    fingerprintLockEnabled: true,
    highContrastBlack: false,
    rulesActiveTab: 'BLOCKED', // 'BLOCKED' | 'TRUSTED'

    messages: [
      {
        id: 'msg-1',
        sender: 'Rahul',
        isSavedContact: true,
        channel: 'WHATSAPP',
        fullUrl: 'secure-login-verify.co/kyc-update',
        displayDomain: 'secure-login-verify.co',
        timestamp: '14:32',
        dateGroup: 'Today',
        status: 'SCAM',
        checkedBy: 'Reported by other users and flagged by security scanners',
        explanation: "We blocked it before it could open. Don't enter any passwords or one-time codes on this site."
      },
      {
        id: 'msg-2',
        sender: '+91 98200 11234',
        isSavedContact: false,
        channel: 'SMS',
        fullUrl: 'bit.ly/3xQa9',
        displayDomain: 'bit.ly/3xQa9',
        timestamp: '12:05',
        dateGroup: 'Today',
        status: 'SUSPICIOUS',
        checkedBy: 'URL shortener hiding destination. Flagged by heuristic scanner.',
        explanation: 'This link uses a shortener often associated with phishing campaigns. Proceed with caution.'
      },
      {
        id: 'msg-3',
        sender: 'Delivery Updates',
        isSavedContact: false,
        channel: 'SMS',
        fullUrl: 'track-parcel-info.net/item?id=892',
        displayDomain: 'track-parcel-info.net',
        timestamp: '11:48',
        dateGroup: 'Today',
        status: 'CHECKING',
        checkedBy: 'Awaiting VirusTotal v3 and sandbox response',
        explanation: 'Analyzing indicators in cloud sandbox. Not yet verified as safe.'
      },
      {
        id: 'msg-4',
        sender: 'Mom',
        isSavedContact: true,
        channel: 'WHATSAPP',
        fullUrl: 'https://youtube.com/watch?v=dQw4w9WgXcQ',
        displayDomain: 'youtube.com',
        timestamp: 'Yesterday, 21:10',
        dateGroup: 'Yesterday',
        status: 'NO_THREATS_FOUND',
        checkedBy: 'Verified official domain and clean threat intelligence database',
        explanation: 'No malicious signatures or phishing reports detected.'
      },
      {
        id: 'msg-5',
        sender: 'Office Group',
        isSavedContact: true,
        channel: 'WHATSAPP',
        fullUrl: 'https://docs.google.com/spreadsheets/d/123',
        displayDomain: 'docs.google.com',
        timestamp: 'Yesterday, 17:42',
        dateGroup: 'Yesterday',
        status: 'NO_THREATS_FOUND',
        checkedBy: 'Google Workspace domain verified',
        explanation: 'Official verified domain.'
      },
      {
        id: 'msg-6',
        sender: 'HDFC-BK',
        isSavedContact: false,
        channel: 'SMS',
        fullUrl: 'https://hdfcbank.com/netbanking',
        displayDomain: 'hdfcbank.com',
        timestamp: 'Yesterday, 09:15',
        dateGroup: 'Yesterday',
        status: 'NO_THREATS_FOUND',
        checkedBy: 'Verified banking portal domain',
        explanation: 'Known clean banking gateway.'
      }
    ],

    blockedRules: [
      { id: 'blk-1', type: 'URL', value: 'secure-login-verify.co', subtitle: 'Reported by you · Today' },
      { id: 'blk-2', type: 'URL', value: 'paypa1-support.net', subtitle: 'Reported by you · 25 Oct' },
      { id: 'blk-3', type: 'PHONE', value: '+91 98200 11234', subtitle: 'Reported by you · 24 Oct' },
      { id: 'blk-4', type: 'URL', value: 'free-recharge-offer.in', subtitle: 'Added by your organization · 20 Oct' }
    ],

    trustedRules: [
      { id: 'tst-1', type: 'URL', value: 'bank.example.in', subtitle: 'Trusted by you · 26 Oct' },
      { id: 'tst-2', type: 'EMAIL', value: 'alerts@hdfcbank.net', subtitle: 'Trusted by you · 22 Oct' },
      { id: 'tst-3', type: 'PHONE', value: '+91 22 6160 6161', subtitle: 'Trusted by you · 18 Oct' },
      { id: 'tst-4', type: 'URL', value: 'intranet.example.com', subtitle: 'Trusted by you · ', expires: 'Expires in 15 min' }
    ]
  };

  // DOM Elements
  const screenLock = document.getElementById('screen-lock');
  const appContainer = document.getElementById('app-container');
  const screenHomeSetup = document.getElementById('screen-home-setup');
  const screenHome = document.getElementById('screen-home');
  const screenMessages = document.getElementById('screen-messages');
  const screenDetail = document.getElementById('screen-detail');
  const screenReportConfirmed = document.getElementById('screen-report-confirmed');
  const screenRules = document.getElementById('screen-rules');
  const screenSettings = document.getElementById('screen-settings');
  const bottomNavbar = document.getElementById('bottom-navbar');

  const btnUnlock = document.getElementById('btn-unlock');
  const btnTurnOnChecking = document.getElementById('btn-turn-on-checking');
  const btnSeeAllMessages = document.getElementById('btn-see-all-messages');
  const btnToggleSearch = document.getElementById('btn-toggle-search');
  const searchBarContainer = document.getElementById('search-bar-container');
  const messagesSearchInput = document.getElementById('messages-search-input');
  const btnClearSearch = document.getElementById('btn-clear-search');

  const homeRecentList = document.getElementById('home-recent-list');
  const messagesFullList = document.getElementById('messages-full-list');
  const rulesListContainer = document.getElementById('rules-list-container');

  const tabRulesBlocked = document.getElementById('tab-rules-blocked');
  const tabRulesTrusted = document.getElementById('tab-rules-trusted');
  const rulesExplanatoryText = document.getElementById('rules-explanatory-text');
  const btnAddRuleDialog = document.getElementById('btn-add-rule-dialog');
  const addRuleModal = document.getElementById('add-rule-modal');
  const modalBtnCancel = document.getElementById('modal-btn-cancel');
  const modalBtnSave = document.getElementById('modal-btn-save');

  // Detail View Elements
  const btnDetailBack = document.getElementById('btn-detail-back');
  const btnConfirmedBack = document.getElementById('btn-confirmed-back');
  const btnActionReport = document.getElementById('btn-action-report');
  const btnActionTrust = document.getElementById('btn-action-trust');
  const btnUndoReport = document.getElementById('btn-undo-report');

  // Settings Toggles
  const toggleNotifAccess = document.getElementById('toggle-notif-access');
  const toggleFingerprintLock = document.getElementById('toggle-fingerprint-lock');
  const toggleOledBlack = document.getElementById('toggle-oled-black');
  const settingsNotifLabel = document.getElementById('settings-notif-label');
  const btnSignOut = document.getElementById('btn-sign-out');

  // Alert Toast
  const headsUpAlert = document.getElementById('heads-up-alert');
  const alertTitle = document.getElementById('alert-title');
  const alertBody = document.getElementById('alert-body');
  const alertCloseBtn = document.getElementById('alert-close-btn');

  // Demo Control Buttons
  const demoPhishWhatsapp = document.getElementById('demo-phish-whatsapp');
  const demoSuspiciousSms = document.getElementById('demo-suspicious-sms');
  const demoSafeYoutube = document.getElementById('demo-safe-youtube');
  const demoSimSwap = document.getElementById('demo-sim-swap');
  const demoLockPhone = document.getElementById('demo-lock-phone');
  const statBlockedCount = document.getElementById('stat-blocked-count');
  const statTrustedCount = document.getElementById('stat-trusted-count');

  // Navigation Logic
  function updateUI() {
    // 1. Lock Screen vs App
    if (!state.isUnlocked && state.fingerprintLockEnabled) {
      screenLock.classList.remove('hidden');
      screenLock.classList.add('flex');
      appContainer.classList.add('hidden');
      return;
    } else {
      screenLock.classList.add('hidden');
      screenLock.classList.remove('flex');
      appContainer.classList.remove('hidden');
    }

    // Hide all sub-screens
    [screenHomeSetup, screenHome, screenMessages, screenDetail, screenReportConfirmed, screenRules, screenSettings].forEach(s => {
      s.classList.add('hidden');
      s.classList.remove('flex');
    });

    // 2. Active Screen Selection
    if (state.activeScreen === 'detail') {
      screenDetail.classList.remove('hidden');
      screenDetail.classList.add('flex');
      bottomNavbar.classList.add('hidden');
      renderDetailScreen();
    } else if (state.activeScreen === 'report-confirmed') {
      screenReportConfirmed.classList.remove('hidden');
      screenReportConfirmed.classList.add('flex');
      bottomNavbar.classList.add('hidden');
      renderReportConfirmedScreen();
    } else {
      bottomNavbar.classList.remove('hidden');

      if (state.activeTab === 'home') {
        if (!state.notificationAccessGranted) {
          screenHomeSetup.classList.remove('hidden');
          screenHomeSetup.classList.add('flex');
        } else {
          screenHome.classList.remove('hidden');
          screenHome.classList.add('flex');
          renderHomeScreen();
        }
      } else if (state.activeTab === 'messages') {
        screenMessages.classList.remove('hidden');
        screenMessages.classList.add('flex');
        renderMessagesScreen();
      } else if (state.activeTab === 'rules') {
        screenRules.classList.remove('hidden');
        screenRules.classList.add('flex');
        renderRulesScreen();
      } else if (state.activeTab === 'settings') {
        screenSettings.classList.remove('hidden');
        screenSettings.classList.add('flex');
        renderSettingsScreen();
      }
    }

    // Update Bottom Nav Bar
    updateNavBar();
    updateStats();
  }

  function updateNavBar() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
      const tab = btn.dataset.tab;
      const indicator = btn.querySelector('.nav-indicator');
      const icon = btn.querySelector('.material-symbols-outlined');
      const label = btn.querySelector('.nav-label');

      if (tab === state.activeTab && (state.activeScreen !== 'detail' && state.activeScreen !== 'report-confirmed')) {
        indicator.className = 'nav-indicator h-8 w-16 bg-[#B2EBEC] rounded-[8px] flex items-center justify-center';
        icon.className = 'material-symbols-outlined text-[#002021] fill-icon';
        label.className = 'nav-label text-[12px] font-semibold text-[#002021]';
      } else {
        indicator.className = 'nav-indicator h-8 w-16 flex items-center justify-center';
        icon.className = 'material-symbols-outlined text-[#3F4849]';
        label.className = 'nav-label text-[12px] font-medium text-[#3F4849]';
      }
    });
  }

  function updateStats() {
    statBlockedCount.textContent = state.blockedRules.length;
    statTrustedCount.textContent = state.trustedRules.length;
  }

  // Render Status Badge
  function getBadgeHtml(status) {
    if (status === 'SCAM') {
      return `<span class="bg-[#FFEDEA] text-[#410002] text-[11px] font-medium px-2 py-0.5 rounded-[8px]">Scam</span>`;
    } else if (status === 'SUSPICIOUS') {
      return `<span class="bg-[#FFDEA0] text-[#271900] text-[11px] font-medium px-2 py-0.5 rounded-[8px]">Suspicious</span>`;
    } else if (status === 'CHECKING') {
      return `<span class="bg-[#E0E3E3] text-[#3F4849] text-[11px] font-medium px-2 py-0.5 rounded-[8px]">Checking</span>`;
    } else {
      return `<span class="bg-[#DAE4E5] text-[#3F4849] text-[11px] font-medium px-2 py-0.5 rounded-[8px]">No threats found</span>`;
    }
  }

  function getStatusIcon(status) {
    if (status === 'SCAM') {
      return `<span class="material-symbols-outlined text-[#BA1A1A] text-[24px]">dangerous</span>`;
    } else if (status === 'SUSPICIOUS') {
      return `<span class="material-symbols-outlined text-[#7D5700] text-[24px]">warning</span>`;
    } else if (status === 'CHECKING') {
      return `<span class="material-symbols-outlined text-[#3F4849] text-[24px]">schedule</span>`;
    } else {
      return `<span class="material-symbols-outlined text-[#3F4849] text-[24px]">check_circle</span>`;
    }
  }

  // Screen 03: Home Render
  function renderHomeScreen() {
    const recent = state.messages.slice(0, 5);
    homeRecentList.innerHTML = recent.map((item, idx) => `
      <div class="py-3 px-1 flex items-start gap-4 cursor-pointer hover:bg-slate-100/50 rounded-lg transition-colors" data-id="${item.id}">
        <div class="pt-0.5 select-none">
          ${getStatusIcon(item.status)}
        </div>
        <div class="flex-1 min-w-0">
          <div class="text-[16px] font-normal leading-6 text-[#191C1D] truncate">${escapeHtml(item.sender)}</div>
          <div class="font-mono text-[13px] leading-5 text-[#3F4849] truncate">${escapeHtml(item.displayDomain)}</div>
        </div>
        <div class="flex flex-col items-end gap-1.5 flex-shrink-0">
          <span class="text-[11px] text-[#3F4849] font-normal">${item.timestamp.split(', ').pop()}</span>
          ${getBadgeHtml(item.status)}
        </div>
      </div>
      ${idx < recent.length - 1 ? `<div class="h-[1px] bg-[#BFC8C8]/40 mx-1"></div>` : ''}
    `).join('');

    homeRecentList.querySelectorAll('[data-id]').forEach(el => {
      el.addEventListener('click', () => {
        openMessageDetail(el.dataset.id);
      });
    });
  }

  // Screen 04: Messages Render
  function renderMessagesScreen() {
    let filtered = state.messages.filter(m => {
      if (state.activeFilter !== 'ALL' && m.status !== state.activeFilter) return false;
      if (state.searchQuery.trim()) {
        const q = state.searchQuery.toLowerCase();
        return m.sender.toLowerCase().includes(q) ||
               m.displayDomain.toLowerCase().includes(q) ||
               m.fullUrl.toLowerCase().includes(q);
      }
      return true;
    });

    const groups = {};
    filtered.forEach(m => {
      groups[m.dateGroup] = groups[m.dateGroup] || [];
      groups[m.dateGroup].push(m);
    });

    let html = '';
    ['Today', 'Yesterday'].forEach(groupName => {
      if (!groups[groupName] || groups[groupName].length === 0) return;
      html += `<div class="text-[14px] font-medium text-[#191C1D] mt-4 mb-2 px-1">${groupName}</div>`;
      html += groups[groupName].map((item, idx) => `
        <div class="py-3 px-1 flex items-start gap-3 cursor-pointer hover:bg-slate-100/50 rounded-lg transition-colors" data-id="${item.id}">
          <div class="w-10 h-10 rounded-full bg-[#E0E3E3] flex items-center justify-center font-semibold text-[#3F4849] text-base shrink-0">
            ${item.sender.charAt(0).toUpperCase()}
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-1.5">
              <span class="text-[16px] text-[#191C1D] truncate">${escapeHtml(item.sender)}</span>
              <span class="text-xs text-[#3F4849]">· ${item.channel === 'WHATSAPP' ? 'WhatsApp' : 'SMS'}</span>
            </div>
            <div class="font-mono text-[13px] text-[#3F4849] truncate mt-0.5">${escapeHtml(item.displayDomain)}</div>
          </div>
          <div class="flex flex-col items-end gap-1 shrink-0">
            ${getStatusIcon(item.status)}
            <span class="text-[11px] text-[#3F4849]">${item.timestamp.split(', ').pop()}</span>
          </div>
        </div>
        ${idx < groups[groupName].length - 1 ? `<div class="h-[1px] bg-[#BFC8C8]/40 mx-1"></div>` : ''}
      `).join('');
    });

    if (filtered.length === 0) {
      html = `<div class="py-12 text-center text-[#3F4849] text-sm">No messages match filter.</div>`;
    }

    messagesFullList.innerHTML = html;

    messagesFullList.querySelectorAll('[data-id]').forEach(el => {
      el.addEventListener('click', () => {
        openMessageDetail(el.dataset.id);
      });
    });
  }

  // Screen 05: Message Detail Render
  function renderDetailScreen() {
    const item = state.messages.find(m => m.id === state.selectedMessageId) || state.messages[0];
    document.getElementById('detail-from-name').textContent = item.sender;
    document.getElementById('detail-from-meta').textContent = `${item.isSavedContact ? 'Saved contact' : 'Unknown sender'} · ${item.channel === 'WHATSAPP' ? 'WhatsApp' : 'SMS'}`;
    document.getElementById('detail-link').textContent = item.fullUrl;
    document.getElementById('detail-received').textContent = item.timestamp;
    document.getElementById('detail-checked-by').textContent = item.checkedBy;

    const banner = document.getElementById('detail-banner');
    const bannerTitle = document.getElementById('detail-banner-title');
    const bannerDesc = document.getElementById('detail-banner-desc');

    if (item.status === 'SCAM') {
      banner.className = 'm3-card bg-[#FFEDEA] p-4 flex items-start gap-3 mt-1';
      banner.querySelector('.material-symbols-outlined').className = 'material-symbols-outlined text-[#BA1A1A] text-[28px]';
      banner.querySelector('.material-symbols-outlined').textContent = 'dangerous';
      bannerTitle.className = 'text-[17px] font-medium text-[#BA1A1A]';
      bannerTitle.textContent = 'This link is a known scam';
      bannerDesc.textContent = "We blocked it before it could open. Don't enter any passwords or one-time codes on this site.";
    } else if (item.status === 'SUSPICIOUS') {
      banner.className = 'm3-card bg-[#FFDEA0] p-4 flex items-start gap-3 mt-1';
      banner.querySelector('.material-symbols-outlined').className = 'material-symbols-outlined text-[#7D5700] text-[28px]';
      banner.querySelector('.material-symbols-outlined').textContent = 'warning';
      bannerTitle.className = 'text-[17px] font-medium text-[#7D5700]';
      bannerTitle.textContent = 'Suspicious URL Structure';
      bannerDesc.textContent = 'This URL uses obfuscation or unverified routing. Proceed with caution.';
    } else {
      banner.className = 'm3-card bg-[#E0E3E3] p-4 flex items-start gap-3 mt-1';
      banner.querySelector('.material-symbols-outlined').className = 'material-symbols-outlined text-[#3F4849] text-[28px]';
      banner.querySelector('.material-symbols-outlined').textContent = 'check_circle';
      bannerTitle.className = 'text-[17px] font-medium text-[#191C1D]';
      bannerTitle.textContent = 'No threats detected';
      bannerDesc.textContent = 'Clean link signature verified against threat intelligence.';
    }
  }

  // Screen 06: Report Confirmed Render
  function renderReportConfirmedScreen() {
    const item = state.messages.find(m => m.id === state.selectedMessageId) || state.messages[0];
    document.getElementById('confirmed-from').textContent = item.sender;
    document.getElementById('confirmed-link').textContent = item.fullUrl;
    document.getElementById('confirmed-received').textContent = item.timestamp;
  }

  // Screens 07 & 08: Rules Render
  function renderRulesScreen() {
    const isBlocked = state.rulesActiveTab === 'BLOCKED';

    if (isBlocked) {
      tabRulesBlocked.className = 'rules-tab-btn active flex-1 h-10 rounded-[8px] bg-[#6FF6FC] text-[#002022] text-sm font-medium flex items-center justify-center gap-1.5';
      tabRulesBlocked.innerHTML = `<span class="material-symbols-outlined text-[16px]">check</span><span>Blocked</span>`;
      tabRulesTrusted.className = 'rules-tab-btn flex-1 h-10 rounded-[8px] border border-[#BFC8C8] text-[#3F4849] text-sm font-medium flex items-center justify-center gap-1.5';
      tabRulesTrusted.innerHTML = `<span>Trusted</span>`;
      rulesExplanatoryText.textContent = 'Blocked links and senders are stopped on this device and reported to your organization.';
    } else {
      tabRulesTrusted.className = 'rules-tab-btn active flex-1 h-10 rounded-[8px] bg-[#6FF6FC] text-[#002022] text-sm font-medium flex items-center justify-center gap-1.5';
      tabRulesTrusted.innerHTML = `<span class="material-symbols-outlined text-[16px]">check</span><span>Trusted</span>`;
      tabRulesBlocked.className = 'rules-tab-btn flex-1 h-10 rounded-[8px] border border-[#BFC8C8] text-[#3F4849] text-sm font-medium flex items-center justify-center gap-1.5';
      tabRulesBlocked.innerHTML = `<span>Blocked</span>`;
      rulesExplanatoryText.textContent = 'Trusted links and senders are never blocked on this device. Your organization can still block them.';
    }

    const rules = isBlocked ? state.blockedRules : state.trustedRules;

    rulesListContainer.innerHTML = rules.map((r, idx) => `
      <div class="py-3 px-1 flex items-center justify-between gap-3">
        <div class="flex items-center gap-3 flex-1 min-w-0">
          <span class="material-symbols-outlined text-[#3F4849] text-[22px]">
            ${r.type === 'PHONE' ? 'phone' : r.type === 'EMAIL' ? 'mail' : 'link'}
          </span>
          <div class="flex-1 min-w-0">
            <div class="font-mono text-sm text-[#191C1D] truncate">${escapeHtml(r.value)}</div>
            <div class="text-xs text-[#3F4849] flex items-center gap-1.5 mt-0.5">
              <span>${escapeHtml(r.subtitle)}</span>
              ${r.expires ? `
                <span class="bg-[#FFDEA0]/70 text-[#271900] text-[10px] px-1.5 py-0.5 rounded-[4px] font-medium flex items-center gap-0.5">
                  <span class="material-symbols-outlined text-[12px]">timer</span>
                  ${r.expires}
                </span>` : ''}
            </div>
          </div>
        </div>
        <button class="btn-delete-rule w-9 h-9 flex items-center justify-center text-[#3F4849] hover:text-[#BA1A1A] rounded" data-id="${r.id}">
          <span class="material-symbols-outlined text-[20px]">delete</span>
        </button>
      </div>
      ${idx < rules.length - 1 ? `<div class="h-[1px] bg-[#BFC8C8]/40 mx-1"></div>` : ''}
    `).join('');

    rulesListContainer.querySelectorAll('.btn-delete-rule').forEach(b => {
      b.addEventListener('click', () => {
        deleteRule(b.dataset.id);
      });
    });
  }

  // Screen 09: Settings Render
  function renderSettingsScreen() {
    toggleNotifAccess.className = `m3-switch ${state.notificationAccessGranted ? 'on' : 'off'}`;
    settingsNotifLabel.textContent = state.notificationAccessGranted ? 'On' : 'Off';

    toggleFingerprintLock.className = `m3-switch ${state.fingerprintLockEnabled ? 'on' : 'off'}`;
    toggleOledBlack.className = `m3-switch ${state.highContrastBlack ? 'on' : 'off'}`;
  }

  function openMessageDetail(id) {
    state.selectedMessageId = id;
    state.activeScreen = 'detail';
    updateUI();
  }

  function deleteRule(id) {
    if (state.rulesActiveTab === 'BLOCKED') {
      state.blockedRules = state.blockedRules.filter(r => r.id !== id);
    } else {
      state.trustedRules = state.trustedRules.filter(r => r.id !== id);
    }
    updateUI();
  }

  function showAlert(title, body) {
    alertTitle.textContent = title;
    alertBody.textContent = body;
    headsUpAlert.classList.remove('hidden');
    setTimeout(() => {
      headsUpAlert.classList.add('hidden');
    }, 4500);
  }

  function escapeHtml(str) {
    return str ? String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;') : '';
  }

  // Event Listeners Setup
  btnUnlock.addEventListener('click', () => {
    state.isUnlocked = true;
    state.activeScreen = state.notificationAccessGranted ? 'home' : 'home-setup';
    updateUI();
  });

  btnTurnOnChecking.addEventListener('click', () => {
    state.notificationAccessGranted = true;
    updateUI();
  });

  document.querySelectorAll('.nav-account-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      state.activeTab = 'settings';
      state.activeScreen = 'settings';
      updateUI();
    });
  });

  btnSeeAllMessages.addEventListener('click', () => {
    state.activeTab = 'messages';
    state.activeScreen = 'messages';
    updateUI();
  });

  // Search Toggle
  btnToggleSearch.addEventListener('click', () => {
    searchBarContainer.classList.toggle('hidden');
    if (!searchBarContainer.classList.contains('hidden')) {
      messagesSearchInput.focus();
    }
  });

  messagesSearchInput.addEventListener('input', (e) => {
    state.searchQuery = e.target.value;
    renderMessagesScreen();
  });

  btnClearSearch.addEventListener('click', () => {
    state.searchQuery = '';
    messagesSearchInput.value = '';
    searchBarContainer.classList.add('hidden');
    renderMessagesScreen();
  });

  // Filter Chips
  document.querySelectorAll('.filter-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.filter-chip').forEach(c => {
        c.className = 'filter-chip m3-chip h-8 px-3 flex items-center gap-1.5 border border-[#BFC8C8] text-[#3F4849] text-xs font-medium';
        const icon = c.querySelector('.material-symbols-outlined');
        if (icon) icon.remove();
      });

      chip.className = 'filter-chip active m3-chip h-8 px-3 flex items-center gap-1.5 bg-[#6FF6FC] text-[#002022] text-xs font-medium';
      chip.insertAdjacentHTML('afterbegin', `<span class="material-symbols-outlined text-[16px]">check</span>`);

      state.activeFilter = chip.dataset.filter;
      renderMessagesScreen();
    });
  });

  // Bottom Navigation
  document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      state.activeTab = btn.dataset.tab;
      state.activeScreen = btn.dataset.tab;
      updateUI();
    });
  });

  // Detail Actions
  btnDetailBack.addEventListener('click', () => {
    state.activeScreen = state.activeTab;
    updateUI();
  });

  btnConfirmedBack.addEventListener('click', () => {
    state.activeScreen = state.activeTab;
    updateUI();
  });

  btnActionReport.addEventListener('click', () => {
    const item = state.messages.find(m => m.id === state.selectedMessageId);
    if (item) {
      item.status = 'SCAM';
      // Add to blocked rules
      if (!state.blockedRules.some(r => r.value === item.displayDomain)) {
        state.blockedRules.unshift({
          id: 'blk-' + Date.now(),
          type: 'URL',
          value: item.displayDomain,
          subtitle: 'Reported by you · Today'
        });
      }
      state.trustedRules = state.trustedRules.filter(r => r.value !== item.displayDomain);
    }
    state.activeScreen = 'report-confirmed';
    showAlert('Threat Blocked & Reported', `${item.displayDomain} blacklisted across your device and enterprise daemon fleet.`);
    updateUI();
  });

  btnActionTrust.addEventListener('click', () => {
    const item = state.messages.find(m => m.id === state.selectedMessageId);
    if (item) {
      item.status = 'NO_THREATS_FOUND';
      // Add to trusted rules with 15m TTL
      if (!state.trustedRules.some(r => r.value === item.displayDomain)) {
        state.trustedRules.unshift({
          id: 'tst-' + Date.now(),
          type: 'URL',
          value: item.displayDomain,
          subtitle: 'Trusted by you · Today',
          expires: 'Expires in 15 min'
        });
      }
      state.blockedRules = state.blockedRules.filter(r => r.value !== item.displayDomain);
    }
    state.activeScreen = state.activeTab;
    showAlert('Temporary Whitelist Active', `${item.displayDomain} allowed on this device for 15 minutes.`);
    updateUI();
  });

  btnUndoReport.addEventListener('click', () => {
    state.activeScreen = 'detail';
    updateUI();
  });

  // Rules Tab Switcher
  tabRulesBlocked.addEventListener('click', () => {
    state.rulesActiveTab = 'BLOCKED';
    renderRulesScreen();
  });

  tabRulesTrusted.addEventListener('click', () => {
    state.rulesActiveTab = 'TRUSTED';
    renderRulesScreen();
  });

  // Add Rule Modal
  btnAddRuleDialog.addEventListener('click', () => {
    addRuleModal.classList.remove('hidden');
    document.getElementById('modal-rule-value').value = '';
    const modalTtl = document.getElementById('modal-ttl-section');
    if (state.rulesActiveTab === 'TRUSTED') {
      document.querySelector('input[name="modal-list-type"][value="TRUSTED"]').checked = true;
      modalTtl.classList.remove('hidden');
    } else {
      document.querySelector('input[name="modal-list-type"][value="BLOCKED"]').checked = true;
      modalTtl.classList.add('hidden');
    }
  });

  document.querySelectorAll('input[name="modal-list-type"]').forEach(r => {
    r.addEventListener('change', (e) => {
      const modalTtl = document.getElementById('modal-ttl-section');
      if (e.target.value === 'TRUSTED') {
        modalTtl.classList.remove('hidden');
      } else {
        modalTtl.classList.add('hidden');
      }
    });
  });

  modalBtnCancel.addEventListener('click', () => {
    addRuleModal.classList.add('hidden');
  });

  modalBtnSave.addEventListener('click', () => {
    const val = document.getElementById('modal-rule-value').value.trim();
    if (!val) return;
    const type = document.querySelector('input[name="modal-rule-type"]:checked').value;
    const listType = document.querySelector('input[name="modal-list-type"]:checked').value;
    const ttlOption = document.querySelector('input[name="modal-ttl"]:checked')?.value;

    if (listType === 'BLOCKED') {
      state.blockedRules.unshift({
        id: 'blk-' + Date.now(),
        type: type,
        value: val,
        subtitle: 'Added by you · Today'
      });
      state.trustedRules = state.trustedRules.filter(r => r.value !== val);
      state.rulesActiveTab = 'BLOCKED';
    } else {
      state.trustedRules.unshift({
        id: 'tst-' + Date.now(),
        type: type,
        value: val,
        subtitle: 'Trusted by you · Today',
        expires: ttlOption === '15' ? 'Expires in 15 min' : ttlOption === '60' ? 'Expires in 1 hr' : null
      });
      state.blockedRules = state.blockedRules.filter(r => r.value !== val);
      state.rulesActiveTab = 'TRUSTED';
    }

    addRuleModal.classList.add('hidden');
    updateUI();
  });

  // Settings Toggles
  toggleNotifAccess.addEventListener('click', () => {
    state.notificationAccessGranted = !state.notificationAccessGranted;
    renderSettingsScreen();
  });

  toggleFingerprintLock.addEventListener('click', () => {
    state.fingerprintLockEnabled = !state.fingerprintLockEnabled;
    renderSettingsScreen();
  });

  toggleOledBlack.addEventListener('click', () => {
    state.highContrastBlack = !state.highContrastBlack;
    const phone = document.querySelector('.phone-mockup');
    if (state.highContrastBlack) {
      phone.style.backgroundColor = '#000000';
    } else {
      phone.style.backgroundColor = '#FAFDFD';
    }
    renderSettingsScreen();
  });

  btnSignOut.addEventListener('click', () => {
    state.isUnlocked = false;
    updateUI();
  });

  alertCloseBtn.addEventListener('click', () => {
    headsUpAlert.classList.add('hidden');
  });

  // Interactive Demo Dock Scenarios
  demoPhishWhatsapp.addEventListener('click', () => {
    const newMsg = {
      id: 'msg-' + Date.now(),
      sender: 'Rahul',
      isSavedContact: true,
      channel: 'WHATSAPP',
      fullUrl: 'secure-login-verify.co/kyc-update',
      displayDomain: 'secure-login-verify.co',
      timestamp: 'Just now',
      dateGroup: 'Today',
      status: 'SCAM',
      checkedBy: 'Matched active threat blacklist & VirusTotal 14/70 detections',
      explanation: "We blocked it before it could open. Don't enter any passwords or one-time codes on this site."
    };
    state.messages.unshift(newMsg);
    showAlert('WhatsApp Phishing Intercepted!', 'Rahul sent link: secure-login-verify.co (Blocked)');
    updateUI();
  });

  demoSuspiciousSms.addEventListener('click', () => {
    const newMsg = {
      id: 'msg-' + Date.now(),
      sender: '+91 98200 11234',
      isSavedContact: false,
      channel: 'SMS',
      fullUrl: 'bit.ly/3xQa9',
      displayDomain: 'bit.ly/3xQa9',
      timestamp: 'Just now',
      dateGroup: 'Today',
      status: 'SUSPICIOUS',
      checkedBy: 'URL shortener hiding destination. Flagged by heuristic scanner.',
      explanation: 'This link uses a shortener often associated with phishing campaigns. Proceed with caution.'
    };
    state.messages.unshift(newMsg);
    showAlert('Suspicious SMS Flagged', 'Unknown sender +91 98200 11234 sent bit.ly link');
    updateUI();
  });

  demoSafeYoutube.addEventListener('click', () => {
    const newMsg = {
      id: 'msg-' + Date.now(),
      sender: 'Mom',
      isSavedContact: true,
      channel: 'WHATSAPP',
      fullUrl: 'https://youtube.com/watch?v=sample',
      displayDomain: 'youtube.com',
      timestamp: 'Just now',
      dateGroup: 'Today',
      status: 'NO_THREATS_FOUND',
      checkedBy: 'Verified official domain and clean threat intelligence database',
      explanation: 'No malicious signatures detected.'
    };
    state.messages.unshift(newMsg);
    showAlert('Safe Message Passed', 'Mom sent verified YouTube link');
    updateUI();
  });

  demoSimSwap.addEventListener('click', () => {
    state.isUnlocked = false;
    showAlert('SIM Change Detected!', 'IMSI mismatch detected. Session locked. Biometric re-verification required.');
    updateUI();
  });

  demoLockPhone.addEventListener('click', () => {
    state.isUnlocked = false;
    updateUI();
  });

  // Start at Lock Screen
  updateUI();
})();
