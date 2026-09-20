let db = null;
let serviceDb = null;
let currentUser = null;
let currentPage = 'dashboard';
let editingApp = null;
let editingCategory = null;
let editingUser = null;
let allCategories = [];
let keepaliveInterval = null;

function withTimeout(promise, ms, msg) {
  let timer;
  return Promise.race([
    promise,
    new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(msg || 'Timeout')), ms); })
  ]).finally(() => clearTimeout(timer));
}

document.addEventListener('DOMContentLoaded', () => {
  db = SupabaseConfig.getClient();
  serviceDb = SupabaseConfig.getServiceClient();

  const saved = localStorage.getItem('zoro_admin_session');
  if (saved) {
    try {
      currentUser = JSON.parse(saved);
      showMainUI();
    } catch { showLoginScreen(); }
  } else {
    showLoginScreen();
  }
  bindEvents();
  startKeepalive();
});

function showLoginScreen() {
  document.getElementById('login-screen').style.display = 'flex';
  document.getElementById('sidebar').style.display = 'none';
  document.getElementById('topbar').style.display = 'none';
  document.getElementById('app-content').style.display = 'none';
  document.body.classList.add('setup-mode');
}

function showMainUI() {
  document.getElementById('login-screen').style.display = 'none';
  document.getElementById('sidebar').style.display = 'flex';
  document.getElementById('topbar').style.display = 'flex';
  document.getElementById('app-content').style.display = 'block';
  document.body.classList.remove('setup-mode');

  const statusEl = document.getElementById('topbar-status');
  if (statusEl && currentUser) {
    statusEl.textContent = '● ' + currentUser.name;
    statusEl.style.color = 'var(--success)';
  }
  showPage('dashboard');
}

function startKeepalive() {
  if (keepaliveInterval) clearInterval(keepaliveInterval);
  keepaliveInterval = setInterval(async () => {
    try {
      await db.from('apps').select('id').limit(1);
    } catch (e) { /* silent ping */ }
  }, 5 * 60 * 1000);
}

function bindEvents() {
  document.getElementById('login-form').addEventListener('submit', handleLogin);
  document.getElementById('app-form').addEventListener('submit', handleAppSave);
  document.getElementById('category-form').addEventListener('submit', handleCategorySave);
  document.getElementById('user-form').addEventListener('submit', handleUserSave);
  document.getElementById('add-account-form').addEventListener('submit', handleAddAccount);
  document.getElementById('app-search').addEventListener('input', filterApps);
  document.getElementById('category-search').addEventListener('input', filterCategories);
  document.getElementById('user-search').addEventListener('input', filterUsers);
  document.getElementById('app-icon-input').addEventListener('change', e => previewImage(e, 'icon-preview'));
  document.getElementById('app-banner-input').addEventListener('change', e => previewImage(e, 'banner-preview'));

  document.getElementById('app-apk-input').addEventListener('change', e => {
    const f = e.target.files[0];
    document.getElementById('apk-name').textContent = f ? f.name : '';
    document.getElementById('apk-size').textContent = f ? formatSize(f.size) : '';
    if (f) document.getElementById('app-download-url').value = '';
  });
  document.getElementById('app-download-url').addEventListener('input', e => {
    if (e.target.value.trim()) {
      document.getElementById('app-apk-input').value = '';
      document.getElementById('apk-name').textContent = '';
      document.getElementById('apk-size').textContent = '';
    }
  });
  document.getElementById('app-screenshots-input').addEventListener('change', e => {
    const files = Array.from(e.target.files);
    document.getElementById('ss-files-names').textContent = files.map(f => f.name).join(', ') || '';
    if (files.length) document.getElementById('app-screenshots').value = '';
  });
  document.getElementById('app-screenshots').addEventListener('input', e => {
    if (e.target.value.trim()) {
      document.getElementById('app-screenshots-input').value = '';
      document.getElementById('ss-files-names').textContent = '';
    }
  });

  document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', () => {
      const page = item.dataset.page;
      if (page === 'logout') { handleLogout(); return; }
      showPage(page);
    });
  });

  document.querySelectorAll('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', e => {
      if (e.target === overlay) closeModal(overlay.id);
    });
  });

  document.querySelectorAll('.modal-close').forEach(btn => {
    btn.addEventListener('click', () => {
      btn.closest('.modal-overlay').style.display = 'none';
      document.body.style.overflow = '';
    });
  });

  document.getElementById('bulk-delete-btn').addEventListener('click', handleBulkDelete);

  const updateForm = document.getElementById('update-form');
  if (updateForm) updateForm.addEventListener('submit', handleUpdateSave);

  const updateApkInput = document.getElementById('update-apk-input');
  if (updateApkInput) updateApkInput.addEventListener('change', e => {
    const f = e.target.files[0];
    document.getElementById('update-apk-name').textContent = f ? f.name : '';
    if (f) document.getElementById('update-apk-url').value = '';
  });
  const updateApkUrl = document.getElementById('update-apk-url');
  if (updateApkUrl) updateApkUrl.addEventListener('input', e => {
    if (e.target.value.trim()) {
      document.getElementById('update-apk-input').value = '';
      document.getElementById('update-apk-name').textContent = '';
    }
  });
}

function showPage(page) {
  currentPage = page;
  document.querySelectorAll('.page-section').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  const section = document.getElementById(`page-${page}`);
  const nav = document.querySelector(`.nav-item[data-page="${page}"]`);
  if (section) section.classList.add('active');
  if (nav) nav.classList.add('active');
  document.getElementById('page-title').textContent = getPageTitle(page);

  if (page === 'dashboard') loadDashboard();
  else if (page === 'apps') loadApps();
  else if (page === 'categories') loadCategories();
  else if (page === 'users') loadUsers();
  else if (page === 'accounts') renderAccountsList();
  else if (page === 'updates') loadUpdates();
  else if (page === 'control-panel') { loadStorageConfigs(); renderStorageList(currentStorageType); }
}

function getPageTitle(page) {
  const t = { dashboard: 'Dashboard', apps: 'App Management', categories: 'Categories', users: 'User Accounts', accounts: 'Supabase Accounts', updates: 'App Updates', 'control-panel': 'Control Panel' };
  return t[page] || page;
}

// ==================== LOGIN ====================

function handleLogin(e) {
  e.preventDefault();
  const email = document.getElementById('login-email').value.trim().toLowerCase();
  const password = document.getElementById('login-password').value;

  const emailCheck = SupabaseConfig.validateEmail(email);
  if (!emailCheck.valid) {
    showToast(emailCheck.error, 'error');
    return;
  }

  if (email === SupabaseConfig.ADMIN_EMAIL && password === SupabaseConfig.ADMIN_PASSWORD) {
    currentUser = { email, name: SupabaseConfig.ADMIN_NAME, role: SupabaseConfig.ADMIN_ROLE };
    localStorage.setItem('zoro_admin_session', JSON.stringify(currentUser));
    showMainUI();
    showToast('Welcome, Admin!', 'success');
  } else {
    showToast('Invalid admin credentials', 'error');
  }
}

function handleLogout() {
  if (!confirm('Logout from admin panel?')) return;
  currentUser = null;
  localStorage.removeItem('zoro_admin_session');
  showLoginScreen();
  showToast('Logged out', 'success');
}

// ==================== DASHBOARD ====================

async function loadDashboard() {
  try {
    const [appsRes, catsRes, usersRes] = await Promise.all([
      db.from('apps').select('*', { count: 'exact' }),
      db.from('categories').select('*', { count: 'exact' }),
      db.from('user_accounts').select('*', { count: 'exact' })
    ]);

    const apps = appsRes.data || [];
    const cats = catsRes.data || [];
    const users = usersRes.data || [];
    const totalDownloads = apps.reduce((sum, a) => sum + (a.download_count || 0), 0);
    const featured = apps.filter(a => a.is_featured).length;
    const recentApps = apps.filter(a => (Date.now() - new Date(a.created_at).getTime()) < 7 * 24 * 60 * 60 * 1000).length;

    document.getElementById('stat-apps').textContent = apps.length;
    document.getElementById('stat-downloads').textContent = formatNumber(totalDownloads);
    document.getElementById('stat-categories').textContent = cats.length;
    document.getElementById('stat-recent').textContent = recentApps;
    document.getElementById('stat-featured').textContent = featured;
    document.getElementById('stat-users').textContent = users.length;

    const activityList = document.getElementById('recent-activity');
    const recent = [...apps].sort((a, b) => new Date(b.updated_at || b.created_at) - new Date(a.updated_at || a.created_at)).slice(0, 10);
    activityList.innerHTML = recent.length ? recent.map(app => `
      <div class="activity-item">
        <span class="activity-icon">${app.is_new ? '🆕' : '📦'}</span>
        <div class="activity-info">
          <span class="activity-name">${escHtml(app.name)}</span>
          <span class="activity-time">${timeAgo(app.updated_at || app.created_at)}</span>
        </div>
        <span class="activity-badge ${app.is_featured ? 'badge-featured' : ''}">${app.is_featured ? 'Featured' : app.category || 'Uncategorized'}</span>
      </div>`).join('') : '<p class="empty-text">No recent activity</p>';

    const topApps = [...apps].sort((a, b) => (b.download_count || 0) - (a.download_count || 0)).slice(0, 5);
    document.getElementById('top-apps').innerHTML = topApps.length ? topApps.map((app, i) => `
      <div class="activity-item">
        <span class="activity-icon">#${i + 1}</span>
        <div class="activity-info">
          <span class="activity-name">${escHtml(app.name)}</span>
          <span class="activity-time">${formatNumber(app.download_count || 0)} downloads</span>
        </div>
        <span class="activity-badge">${app.rating ? app.rating.toFixed(1) + ' ★' : 'N/A'}</span>
      </div>`).join('') : '<p class="empty-text">No apps yet</p>';
  } catch (err) {
    showToast('Dashboard error: ' + err.message, 'error');
  }
}

// ==================== APPS ====================

let allApps = [];
let selectedAppIds = new Set();

async function loadApps() {
  try {
    const { data, error } = await db.from('apps').select('*').order('created_at', { ascending: false });
    if (error) throw error;
    allApps = data || [];
    renderAppsTable(allApps);
    updateBulkActions();
    await loadCategoriesForDropdown();
  } catch (err) {
    showToast('Failed to load apps: ' + err.message, 'error');
  }
}

function renderAppsTable(apps) {
  const tbody = document.getElementById('apps-tbody');
  if (!apps.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="empty-row">No apps yet. Click "+ Add App".</td></tr>';
    return;
  }
  tbody.innerHTML = apps.map(app => {
    const isUploading = app.upload_status === 'uploading';
    const isFailed = app.upload_status === 'failed';
    const rowStyle = isUploading ? 'background:#1a1a2e;' : isFailed ? 'background:#2a1a1a;' : '';
    const uploadPct = isUploading ? (appUploadProgress[app.id] || 0) : 0;
    return `
    <tr data-id="${app.id}" style="${rowStyle}">
      <td><input type="checkbox" class="row-check" data-id="${app.id}" ${selectedAppIds.has(app.id) ? 'checked' : ''}></td>
      <td>
        <div class="app-cell">
          ${app.icon_url ? `<img src="${escAttr(app.icon_url)}" class="app-icon-small">` : '<div class="app-icon-placeholder">📦</div>'}
          <div>
            <div class="app-cell-name">${escHtml(app.name)}</div>
            <div class="app-cell-pkg">${escHtml(app.package_name)}</div>
            ${isUploading ? `<div class="app-upload-progress"><div class="app-upload-bar" style="width:${uploadPct}%"></div><span class="app-upload-label">Uploading ${uploadPct}%</span></div>` : ''}
          </div>
        </div>
      </td>
      <td>${escHtml(app.version_name || '')}</td>
      <td><span class="cat-badge">${escHtml(app.category || 'Uncategorized')}</span></td>
      <td>${formatNumber(app.download_count || 0)}</td>
      <td>${app.rating ? app.rating.toFixed(1) + ' ★' : 'N/A'}</td>
      <td>
        <div class="badges">
          ${app.is_featured ? '<span class="badge badge-featured">Featured</span>' : ''}
          ${app.is_new ? '<span class="badge badge-new">New</span>' : ''}
          ${app.is_verified ? '<span class="badge badge-verified">Verified</span>' : ''}
          ${app.is_share_only ? '<span class="badge badge-share-only">Share Only</span>' : ''}
          ${isUploading ? '<span class="badge badge-uploading">Uploading</span>' : ''}
          ${isFailed ? '<span class="badge badge-failed">Failed</span>' : ''}
          ${app.upload_status === 'uploaded' ? '' : ''}
          ${app.upload_status === 'linked' ? '' : ''}
        </div>
      </td>
      <td class="actions-cell">
        <button class="btn-icon btn-share" title="Share" onclick="copyShareLink('${app.id}', '${escAttr(app.name || '')}', '${escAttr(app.version_name || '')}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
        </button>
        <button class="btn-icon btn-edit" title="Edit" onclick="editApp('${app.id}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
        </button>
        <button class="btn-icon btn-delete" title="Delete" onclick="deleteApp('${app.id}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
        ${isFailed ? `<button class="btn-icon btn-retry" title="Retry Upload" onclick="retryUpload('${app.id}')" style="color:#ffa726;">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
        </button>` : ''}
      </td>
    </tr>`;
  }).join('');

  tbody.querySelectorAll('.row-check').forEach(cb => {
    cb.addEventListener('change', () => {
      if (cb.checked) selectedAppIds.add(cb.dataset.id);
      else selectedAppIds.delete(cb.dataset.id);
      updateBulkActions();
    });
  });
}

function filterApps() {
  const q = document.getElementById('app-search').value.toLowerCase();
  renderAppsTable(allApps.filter(a =>
    a.name.toLowerCase().includes(q) ||
    (a.package_name || '').toLowerCase().includes(q) ||
    (a.category || '').toLowerCase().includes(q)
  ));
}

function updateBulkActions() {
  const bar = document.getElementById('bulk-actions');
  bar.style.display = selectedAppIds.size > 0 ? 'flex' : 'none';
  document.getElementById('bulk-count').textContent = `${selectedAppIds.size} selected`;
}

function handleBulkDelete() {
  if (!confirm(`Delete ${selectedAppIds.size} apps?`)) return;
  bulkDeleteApps([...selectedAppIds]);
}

async function bulkDeleteApps(ids) {
  try {
    for (const id of ids) {
      const { error } = await serviceDb.from('apps').delete().eq('id', id);
      if (error) throw error;
    }
    selectedAppIds.clear();
    showToast(`${ids.length} apps deleted`, 'success');
    loadApps();
  } catch (err) { showToast('Delete failed: ' + err.message, 'error'); }
}

async function loadCategoriesForDropdown() {
  try {
    const { data } = await db.from('categories').select('*').order('order_index');
    allCategories = data || [];
    const select = document.getElementById('app-category-select');
    if (select) {
      select.innerHTML = '<option value="">Select category...</option>' +
        allCategories.map(c => `<option value="${escAttr(c.name)}">${escHtml(c.icon || '')} ${escHtml(c.name)}</option>`).join('');
    }
  } catch (e) {}
}

function openAddApp() {
  editingApp = null;
  document.getElementById('app-modal-title').textContent = 'Add New App';
  document.getElementById('app-form').reset();
  document.getElementById('app-id').value = '';
  ['icon-preview', 'banner-preview'].forEach(id => {
    const el = document.getElementById(id);
    if (el) { el.src = ''; el.style.display = 'none'; }
  });
  ['apk-name', 'apk-size', 'ss-files-names'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.textContent = '';
  });
  loadCategoriesForDropdown();
  loadStorageSourceDropdown();
  openModal('app-modal');
}

function loadStorageSourceDropdown() {
  loadStorageConfigs();
  const select = document.getElementById('app-storage-source');
  if (!select) return;
  select.innerHTML = '<option value="">Auto (use active storage)</option>';
  const providers = [
    { key: 'supabase', label: 'Supabase', subKey: 'sup-url', idKey: 'sup-id', nameKey: 'sup-name' },
    { key: 'clorabase', label: 'GitHub', subKey: 'cla-repo', idKey: 'cla-id', nameKey: 'cla-name' },
    { key: 'cloudinary', label: 'Cloudinary', subKey: 'cloud-name', idKey: 'cloud-id', nameKey: 'cloud-name' },
    { key: 'pcloud', label: 'pCloud', subKey: 'pc-email', idKey: 'pc-id', nameKey: 'pc-name' },
    { key: 'archiveorg', label: 'Archive.org', subKey: 'ao-access-key', idKey: 'ao-id', nameKey: 'ao-name' },
    { key: 'pixeldrain', label: 'Pixeldrain', subKey: 'pd-api-key', idKey: 'pd-id', nameKey: 'pd-name' }
  ];
  for (const p of providers) {
    const accounts = allStorageConfigs[p.key] || [];
    for (const acc of accounts) {
      const name = acc[p.nameKey] || acc.name || acc.id || p.label;
      const isAct = acc.isActive ? ' (active)' : '';
      select.innerHTML += `<option value="${p.key}:${acc.id}">${p.label}: ${name}${isAct}</option>`;
    }
  }
}

async function editApp(id) {
  try {
    const { data, error } = await db.from('apps').select('*').eq('id', id).single();
    if (error) throw error;
    editingApp = data;
    document.getElementById('app-modal-title').textContent = 'Edit App';
    document.getElementById('app-id').value = data.id;
    document.getElementById('app-name').value = data.name || '';
    document.getElementById('app-package-name').value = data.package_name || '';
    document.getElementById('app-version-name').value = data.version_name || '';
    document.getElementById('app-version-code').value = data.version_code || '';
    document.getElementById('app-description').value = data.description || '';
    document.getElementById('app-short-description').value = data.short_description || '';
    document.getElementById('app-developer').value = data.developer_name || '';
    document.getElementById('app-developer-email').value = data.developer_email || '';
    document.getElementById('app-developer-website').value = data.developer_website || '';
    document.getElementById('app-download-url').value = data.download_url || '';
    document.getElementById('app-file-size').value = data.file_size || '';
    document.getElementById('app-min-sdk').value = data.min_sdk || '';
    document.getElementById('app-target-sdk').value = data.target_sdk || '';
    document.getElementById('app-release-date').value = data.release_date ? data.release_date.split('T')[0] : '';
    document.getElementById('app-last-updated').value = data.last_updated ? data.last_updated.slice(0, 16) : '';
    document.getElementById('app-rating').value = data.rating || '';
    document.getElementById('app-review-count').value = data.review_count || '';
    document.getElementById('app-download-count').value = data.download_count || 0;
    document.getElementById('app-whats-new').value = data.whats_new || '';
    document.getElementById('app-changelog').value = data.changelog || '';
    document.getElementById('app-signature-hash').value = data.signature_hash || '';
    document.getElementById('app-signing-cert').value = data.signing_certificate || '';
    document.getElementById('app-featured').checked = data.is_featured || false;
    document.getElementById('app-new').checked = data.is_new || false;
    document.getElementById('app-verified').checked = data.is_verified || false;
    document.getElementById('app-updated').checked = data.is_updated || false;
    document.getElementById('app-share-only').checked = data.is_share_only || false;

    await loadCategoriesForDropdown();
    if (data.category) document.getElementById('app-category-select').value = data.category;
    loadStorageSourceDropdown();
    if (data.upload_source) document.getElementById('app-storage-source').value = data.upload_source;
    if (data.icon_url) { document.getElementById('icon-preview').src = data.icon_url; document.getElementById('icon-preview').style.display = 'block'; }
    if (data.banner_url) { document.getElementById('banner-preview').src = data.banner_url; document.getElementById('banner-preview').style.display = 'block'; }
    if (data.tags) document.getElementById('app-tags').value = Array.isArray(data.tags) ? data.tags.join(', ') : data.tags;
    if (data.permissions) document.getElementById('app-permissions').value = Array.isArray(data.permissions) ? data.permissions.join(', ') : data.permissions;
    if (data.screenshot_urls && Array.isArray(data.screenshot_urls) && data.screenshot_urls.length) {
      document.getElementById('app-screenshots').value = data.screenshot_urls.join('\n');
    }
    openModal('app-modal');
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

async function handleAppSave(e) {
  e.preventDefault();
  const id = document.getElementById('app-id').value;
  const isEdit = !!id;

  const apkFile = document.getElementById('app-apk-input').files[0];
  const downloadUrl = document.getElementById('app-download-url').value.trim();
  const storageSource = document.getElementById('app-storage-source').value;
  if (!apkFile && !downloadUrl) { showToast('Provide APK file OR Download URL', 'error'); return; }

  const ssFiles = Array.from(document.getElementById('app-screenshots-input').files || []);
  const ssUrls = document.getElementById('app-screenshots').value.trim();
  if (!ssFiles.length && !ssUrls) { showToast('Provide Screenshots (file or URL)', 'error'); return; }

  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;

  const progressContainer = document.getElementById('upload-progress-container');
  const progressBar = document.getElementById('upload-progress-bar');
  const progressText = document.getElementById('upload-progress-text');
  const progressPercent = document.getElementById('upload-progress-percent');

  if (apkFile) {
    btn.innerHTML = '<span class="spinner"></span> Preparing upload...';
  }

  let appRowId = id;

  try {
    const iconFile = document.getElementById('app-icon-input').files[0];
    const bannerFile = document.getElementById('app-banner-input').files[0];

    let iconUrl = editingApp?.icon_url || '';
    let bannerUrl = editingApp?.banner_url || '';
    let finalDownloadUrl = downloadUrl;
    let screenshotUrls = [];

    const appData = {
      name: document.getElementById('app-name').value,
      package_name: document.getElementById('app-package-name').value,
      version_name: document.getElementById('app-version-name').value,
      version_code: parseInt(document.getElementById('app-version-code').value) || 0,
      description: document.getElementById('app-description').value,
      short_description: document.getElementById('app-short-description').value,
      category: document.getElementById('app-category-select').value,
      developer_name: document.getElementById('app-developer').value,
      developer_email: document.getElementById('app-developer-email').value,
      developer_website: document.getElementById('app-developer-website').value,
      download_url: finalDownloadUrl,
      file_size: parseInt(document.getElementById('app-file-size').value) || 0,
      min_sdk: parseInt(document.getElementById('app-min-sdk').value) || 0,
      target_sdk: parseInt(document.getElementById('app-target-sdk').value) || 0,
      release_date: document.getElementById('app-release-date').value || null,
      last_updated: document.getElementById('app-last-updated').value || new Date().toISOString(),
      rating: parseFloat(document.getElementById('app-rating').value) || 0,
      review_count: parseInt(document.getElementById('app-review-count').value) || 0,
      download_count: parseInt(document.getElementById('app-download-count').value) || 0,
      whats_new: document.getElementById('app-whats-new').value,
      changelog: document.getElementById('app-changelog').value,
      signature_hash: document.getElementById('app-signature-hash').value,
      signing_certificate: document.getElementById('app-signing-cert').value,
      is_featured: document.getElementById('app-featured').checked,
      is_new: document.getElementById('app-new').checked,
      is_verified: document.getElementById('app-verified').checked,
      is_updated: document.getElementById('app-updated').checked,
      is_share_only: document.getElementById('app-share-only').checked,
      icon_url: iconUrl,
      banner_url: bannerUrl,
      screenshot_urls: screenshotUrls,
      tags: document.getElementById('app-tags').value.trim() ? document.getElementById('app-tags').value.split(',').map(t => t.trim()).filter(Boolean) : [],
      permissions: document.getElementById('app-permissions').value.trim() ? document.getElementById('app-permissions').value.split(',').map(p => p.trim()).filter(Boolean) : [],
      upload_status: apkFile ? 'uploading' : (downloadUrl ? 'linked' : 'pending'),
      upload_source: storageSource || '',
      updated_at: new Date().toISOString()
    };

    if (isEdit) {
      const result = await serviceDb.from('apps').update(appData).eq('id', id);
      if (result.error) throw result.error;
    } else {
      appData.created_at = new Date().toISOString();
      const result = await serviceDb.from('apps').insert(appData).select().single();
      if (result.error) throw result.error;
      appRowId = result.data.id;
    }

    if (iconFile) iconUrl = await withTimeout(uploadFile('icons', iconFile, storageSource), 60000, 'Icon upload timed out');
    if (bannerFile) bannerUrl = await withTimeout(uploadFile('banners', bannerFile, storageSource), 60000, 'Banner upload timed out');
    if (apkFile) {
      progressContainer.style.display = 'flex';
      finalDownloadUrl = await withTimeout(uploadFileWithProgress('apps', apkFile, (pct) => {
        progressBar.style.width = pct + '%';
        progressPercent.textContent = pct + '%';
        progressText.textContent = `Uploading ${apkFile.name} (${(apkFile.size / 1024 / 1024).toFixed(1)} MB)...`;
        btn.innerHTML = `<span class="spinner"></span> Uploading APK ${pct}%`;
        if (appRowId) {
          appUploadProgress[appRowId] = pct;
          const row = document.querySelector(`tr[data-id="${appRowId}"]`);
          if (row) {
            const bar = row.querySelector('.app-upload-bar');
            const label = row.querySelector('.app-upload-label');
            if (bar) bar.style.setProperty('--pct', pct + '%');
            if (label) label.textContent = 'Uploading ' + pct + '%';
          }
        }
      }, storageSource), 300000, 'APK upload timed out');
      progressContainer.style.display = 'none';
    }
    if (ssFiles.length) {
      for (const f of ssFiles) screenshotUrls.push(await withTimeout(uploadFile('screenshots', f, storageSource), 60000, 'Screenshot upload timed out'));
    } else {
      screenshotUrls = ssUrls.split('\n').map(s => s.trim()).filter(Boolean);
    }

    const finalData = {
      icon_url: iconUrl,
      banner_url: bannerUrl,
      download_url: finalDownloadUrl,
      screenshot_urls: screenshotUrls,
      upload_status: apkFile ? 'uploaded' : (downloadUrl ? 'linked' : 'pending'),
      updated_at: new Date().toISOString()
    };

    await serviceDb.from('apps').update(finalData).eq('id', appRowId);

    if (appData.package_name && appData.version_code && finalDownloadUrl) {
      const { data: existing } = await db.from('app_updates')
        .select('version_code')
        .eq('package_name', appData.package_name)
        .order('version_code', { ascending: false })
        .limit(1);
      const latestCode = existing?.[0]?.version_code || 0;
      if (appData.version_code > latestCode) {
        await serviceDb.from('app_updates').insert({
          package_name: appData.package_name,
          version_name: appData.version_name,
          version_code: appData.version_code,
          apk_url: finalDownloadUrl,
          release_notes: appData.whats_new || '',
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        });
      }
    }

    closeModal('app-modal');
    showToast(isEdit ? 'App updated!' : 'App created!', 'success');
    loadApps();
  } catch (err) {
    showToast('Save failed: ' + err.message, 'error');
    if (apkFile && appRowId) {
      await serviceDb.from('apps').update({ upload_status: 'failed' }).eq('id', appRowId).catch(() => {});
      loadApps();
    }
  }
  finally {
    btn.disabled = false;
    btn.innerHTML = 'Save App';
    progressContainer.style.display = 'none';
    progressBar.style.width = '0%';
    progressText.textContent = '';
    progressPercent.textContent = '';
    if (appRowId) {
      delete appUploadProgress[appRowId];
      loadApps();
    }
  }
}

async function deleteApp(id) {
  if (!confirm('Delete this app?')) return;
  try {
    const { error } = await serviceDb.from('apps').delete().eq('id', id);
    if (error) throw error;
    showToast('App deleted', 'success');
    loadApps();
  } catch (err) { showToast('Delete failed: ' + err.message, 'error'); }
}

async function retryUpload(appId) {
  try {
    const { data: app, error } = await db.from('apps').select('*').eq('id', appId).single();
    if (error || !app) { showToast('App not found', 'error'); return; }
    await serviceDb.from('apps').update({ upload_status: 'uploading' }).eq('id', appId);
    showToast('Retrying upload for ' + app.name, 'success');
    loadApps();
    editApp(appId);
  } catch (err) { showToast('Retry failed: ' + err.message, 'error'); }
}

const appUploadProgress = {};

function resolveSourceAccount(source) {
  if (!source) return null;
  const [provider, id] = source.split(':');
  const accounts = allStorageConfigs[provider] || [];
  return accounts.find(a => (a.id || a['sup-id'] || a['cla-id'] || a['cloud-id'] || a['pc-id']) === id) || null;
}

function uploadFileWithProgress(bucket, file, onProgress, source) {
  return new Promise((resolve, reject) => {
    loadStorageConfigs();
    const specific = resolveSourceAccount(source);
    if (specific) {
      const provider = (source || '').split(':')[0];
      if (provider === 'supabase') {
        const url = specific['sup-url'] || specific.url;
        const serviceKey = specific['sup-service-key'] || specific['sup-anon-key'];
        const bucketName = specific['sup-bucket'] || bucket;
        if (!url || !serviceKey) { reject(new Error('Supabase URL or key missing')); return; }
        const ext = file.name.split('.').pop();
        const path = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${ext}`;
        const xhr = new XMLHttpRequest();
        xhr.open('POST', `${url}/storage/v1/object/${bucketName}/${path}`, true);
        xhr.setRequestHeader('apikey', serviceKey);
        xhr.setRequestHeader('Authorization', `Bearer ${serviceKey}`);
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
        };
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(`${url}/storage/v1/object/public/${bucketName}/${path}`);
          } else { reject(new Error(`Upload failed: ${xhr.status}`)); }
        };
        xhr.onerror = () => reject(new Error('Network error'));
        xhr.send(file);
        return;
      }
      if (provider === 'clorabase') { uploadFileViaGitHub(specific, file).then(resolve).catch(reject); return; }
      if (provider === 'cloudinary') { uploadFileViaCloudinary(specific, file).then(resolve).catch(reject); return; }
      if (provider === 'pcloud') { uploadFileViaPcloud(specific, file).then(resolve).catch(reject); return; }
      if (provider === 'archiveorg') { uploadFileViaArchiveOrg(specific, file).then(resolve).catch(reject); return; }
      if (provider === 'pixeldrain') { uploadFileViaPixeldrain(specific, file).then(resolve).catch(reject); return; }
    }

    const activeGh = (allStorageConfigs.clorabase || []).find(a => a.isActive);
    if (activeGh) { uploadFileViaGitHub(activeGh, file).then(resolve).catch(reject); return; }
    const activeSupabase = (allStorageConfigs.supabase || []).find(a => a.isActive);
    if (activeSupabase) {
      const url = activeSupabase['sup-url'] || activeSupabase.url;
      const serviceKey = activeSupabase['sup-service-key'] || activeSupabase['sup-anon-key'];
      const bucketName = activeSupabase['sup-bucket'] || bucket;
      if (!url || !serviceKey) { reject(new Error('Supabase URL or key missing')); return; }
      const ext = file.name.split('.').pop();
      const path = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${ext}`;
      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${url}/storage/v1/object/${bucketName}/${path}`, true);
      xhr.setRequestHeader('apikey', serviceKey);
      xhr.setRequestHeader('Authorization', `Bearer ${serviceKey}`);
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(`${url}/storage/v1/object/public/${bucketName}/${path}`);
        } else { reject(new Error(`Upload failed: ${xhr.status}`)); }
      };
      xhr.onerror = () => reject(new Error('Network error'));
      xhr.send(file);
      return;
    }
    const activeCloudinary = (allStorageConfigs.cloudinary || []).find(a => a.isActive);
    if (activeCloudinary) { uploadFileViaCloudinary(activeCloudinary, file).then(resolve).catch(reject); return; }
    const activePcloud = (allStorageConfigs.pcloud || []).find(a => a.isActive);
    if (activePcloud) { uploadFileViaPcloud(activePcloud, file).then(resolve).catch(reject); return; }
    const activeArchiveOrg = (allStorageConfigs.archiveorg || []).find(a => a.isActive);
    if (activeArchiveOrg) { uploadFileViaArchiveOrg(activeArchiveOrg, file).then(resolve).catch(reject); return; }
    const activePixeldrain = (allStorageConfigs.pixeldrain || []).find(a => a.isActive);
    if (activePixeldrain) { uploadFileViaPixeldrain(activePixeldrain, file).then(resolve).catch(reject); return; }
    reject(new Error('No active storage account found.'));
  });
}

async function uploadFile(bucket, file, source) {
  loadStorageConfigs();
  const specific = resolveSourceAccount(source);
  if (specific) {
    const provider = (source || '').split(':')[0];
    if (provider === 'supabase') return await uploadFileViaSupabase(specific, bucket, file);
    if (provider === 'clorabase') return await uploadFileViaGitHub(specific, file);
    if (provider === 'cloudinary') return await uploadFileViaCloudinary(specific, file);
    if (provider === 'pcloud') return await uploadFileViaPcloud(specific, file);
    if (provider === 'archiveorg') return await uploadFileViaArchiveOrg(specific, file);
    if (provider === 'pixeldrain') return await uploadFileViaPixeldrain(specific, file);
  }

  const activeGh = (allStorageConfigs.clorabase || []).find(a => a.isActive);
  if (activeGh) {
    try {
      return await uploadFileViaGitHub(activeGh, file);
    } catch (e) { console.log('GitHub upload failed:', e.message); }
  }

  const activeSupabase = (allStorageConfigs.supabase || []).find(a => a.isActive);
  if (activeSupabase) {
    try {
      return await uploadFileViaSupabase(activeSupabase, bucket, file);
    } catch (e) { console.log('Supabase upload failed:', e.message); }
  }

  const activeCloudinary = (allStorageConfigs.cloudinary || []).find(a => a.isActive);
  if (activeCloudinary) {
    try {
      return await uploadFileViaCloudinary(activeCloudinary, file);
    } catch (e) { console.log('Cloudinary upload failed:', e.message); }
  }

  const activePcloud = (allStorageConfigs.pcloud || []).find(a => a.isActive);
  if (activePcloud) {
    try {
      return await uploadFileViaPcloud(activePcloud, file);
    } catch (e) { console.log('pCloud upload failed:', e.message); }
  }

  const activeArchiveOrg = (allStorageConfigs.archiveorg || []).find(a => a.isActive);
  if (activeArchiveOrg) {
    try {
      return await uploadFileViaArchiveOrg(activeArchiveOrg, file);
    } catch (e) { console.log('Archive.org upload failed:', e.message); }
  }

  const activePixeldrain = (allStorageConfigs.pixeldrain || []).find(a => a.isActive);
  if (activePixeldrain) {
    try {
      return await uploadFileViaPixeldrain(activePixeldrain, file);
    } catch (e) { console.log('Pixeldrain upload failed:', e.message); }
  }

  throw new Error('No active storage account found. Go to Control Panel → Storage Settings and activate one.');
}

async function uploadFileViaSupabase(account, bucket, file) {
  const url = account['sup-url'] || account.url;
  const serviceKey = account['sup-service-key'] || account['sup-anon-key'] || account['sup-anon-key'];
  const bucketName = account['sup-bucket'] || bucket;
  if (!url || !serviceKey) throw new Error('Supabase URL or key missing');

  const client = supabase.createClient(url, serviceKey);
  const ext = file.name.split('.').pop();
  const path = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${ext}`;

  const { error } = await client.storage.from(bucketName).upload(path, file, { upsert: true });
  if (error) throw error;
  const { data: urlData } = client.storage.from(bucketName).getPublicUrl(path);
  return urlData.publicUrl;
}

async function uploadFileViaGitHub(account, file) {
  const repo = account['cla-repo'];
  const token = account['cla-token'];
  const tag = account['cla-branch'] || `upload-${Date.now()}`;
  if (!repo || !token) throw new Error('GitHub repo or token missing');

  const headers = { Authorization: `token ${token}`, Accept: 'application/vnd.github.v3+json' };
  let release;
  try {
    const r = await fetch(`https://api.github.com/repos/${repo}/releases/tags/${tag}`, { headers });
    if (r.ok) { release = await r.json(); }
  } catch {}

  if (!release) {
    const r = await fetch(`https://api.github.com/repos/${repo}/releases`, {
      method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ tag_name: tag, name: tag, draft: false })
    });
    if (!r.ok) throw new Error('Failed to create release');
    release = await r.json();
  }

  const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_');
  const uploadUrl = release.upload_url.replace(/\{.*\}/, `?name=${safeName}`);
  const formData = new FormData();
  formData.append('file', file);

  const r = await fetch(uploadUrl, { method: 'POST', headers: { Authorization: `token ${token}` }, body: formData });
  if (!r.ok) throw new Error('Upload failed');
  const asset = await r.json();
  return asset.browser_download_url;
}

async function uploadFileViaCloudinary(account, file) {
  const cloudName = account['cld-cloud-name'];
  const apiKey = account['cld-api-key'];
  const apiSecret = account['cld-api-secret'];
  const folder = account['cld-folder'] || 'apk-store';
  if (!cloudName || !apiKey || !apiSecret) throw new Error('Cloudinary credentials missing');

  const timestamp = Math.round(Date.now() / 1000);
  const paramsToSign = `folder=${folder}&timestamp=${timestamp}${apiSecret}`;
  const encoder = new TextEncoder();
  const data = encoder.encode(paramsToSign);
  const hashBuffer = await crypto.subtle.digest('SHA-1', data);
  const signature = Array.from(new Uint8Array(hashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('');

  const formData = new FormData();
  formData.append('file', file);
  formData.append('api_key', apiKey);
  formData.append('timestamp', timestamp);
  formData.append('folder', folder);
  formData.append('signature', signature);

  const r = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/auto/upload`, { method: 'POST', body: formData });
  if (!r.ok) throw new Error('Cloudinary upload failed');
  const result = await r.json();
  return result.secure_url;
}

async function uploadFileViaPcloud(account, file) {
  const token = account['pcloud-token'];
  const folder = account['pcloud-folder'] || '/AppStore';
  const region = account['pcloud-region'] || 'us';
  if (!token) throw new Error('pCloud token missing');

  const apiBase = region === 'eu' ? 'https://eapi.pcloud.com' : 'https://api.pcloud.com';
  const folderRes = await fetch(`${apiBase}/listfolder?path=${encodeURIComponent(folder)}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  let folderId = 0;
  if (folderRes.ok) {
    const fd = await folderRes.json();
    if (fd.result === 0 && fd.metadata) folderId = fd.metadata.folderid;
  }

  const upRes = await fetch(`${apiBase}/upload?auth=${token}&folderid=${folderId}`, {
    method: 'POST', body: file
  });
  if (!upRes.ok) throw new Error('pCloud upload failed');
  const upData = await upRes.json();
  if (upData.result !== 0) throw new Error(upData.error || 'pCloud upload failed');

  const fileId = upData.metadata?.fileid;
  const pubRes = await fetch(`${apiBase}/getfilelink?auth=${token}&fileid=${fileId}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!pubRes.ok) throw new Error('pCloud link generation failed');
  const pubData = await pubRes.json();
  const host = pubData?.hosts?.[0];
  if (!host) throw new Error('pCloud no host found');
  return `https://${host}${pubData.path}`;
}

async function uploadFileViaArchiveOrg(account, file) {
  const prefix = account['ao-item-prefix'] || 'apk-store';
  const itemName = prefix + '-' + file.name.replace(/[^a-zA-Z0-9._-]/g, '_').toLowerCase();
  return 'https://archive.org/download/' + itemName + '/' + file.name;
}

async function uploadFileViaPixeldrain(account, file) {
  const apiKey = account['pd-api-key'] || '';
  const formData = new FormData();
  formData.append('file', file);

  const headers = {};
  if (apiKey) headers['Authorization'] = 'Basic ' + btoa(':' + apiKey);

  const r = await fetch('https://pixeldrain.com/api/file', {
    method: 'POST',
    headers,
    body: formData
  });
  if (!r.ok) throw new Error(`Pixeldrain upload failed: ${r.status}`);
  const result = await r.json();
  if (!result.id) throw new Error('Pixeldrain: no file ID returned');
  return `https://pixeldrain.com/api/file/${result.id}`;
}

async function uploadToGitHub(file, versionName) {
  loadStorageConfigs();
  const ghAccounts = allStorageConfigs.clorabase || [];
  const active = ghAccounts.find(a => a.isActive) || ghAccounts[0];
  if (!active) throw new Error('No GitHub Storage account configured. Add one in Control Panel → GitHub Storage.');

  const repo = active['cla-repo'];
  const token = active['cla-token'];
  if (!repo || !token) throw new Error('GitHub repo or token missing.');

  const tag = versionName || `v${Date.now()}`;
  const releaseName = `${file.name} - ${tag}`;

  let release;
  try {
    const relRes = await fetch(`https://api.github.com/repos/${repo}/releases/tags/${tag}`, {
      headers: { 'Authorization': `token ${token}`, 'Accept': 'application/vnd.github.v3+json' }
    });
    if (relRes.ok) {
      release = await relRes.json();
    }
  } catch {}

  if (!release) {
    const createRes = await fetch(`https://api.github.com/repos/${repo}/releases`, {
      method: 'POST',
      headers: {
        'Authorization': `token ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'application/vnd.github.v3+json'
      },
      body: JSON.stringify({ tag_name: tag, name: releaseName, draft: false, prerelease: false })
    });
    if (!createRes.ok) {
      const err = await createRes.json();
      throw new Error(err.message || 'Failed to create GitHub release');
    }
    release = await createRes.json();
  }

  const uploadUrl = `https://uploads.github.com/repos/${repo}/releases/${release.id}/assets?name=${encodeURIComponent(file.name)}`;
  const formData = new FormData();
  formData.append('file', file);

  const uploadRes = await fetch(uploadUrl, {
    method: 'POST',
    headers: {
      'Authorization': `token ${token}`,
      'Content-Type': file.type || 'application/vnd.android.package-archive'
    },
    body: formData
  });

  if (!uploadRes.ok) {
    const err = await uploadRes.json();
    throw new Error(err.message || 'Failed to upload APK to GitHub');
  }

  const asset = await uploadRes.json();
  return asset.browser_download_url;
}

function copyShareLink(appId, appName, appVersion) {
  const slug = (appName || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  const ver = appVersion || '1.0';
  const link = `https://apk-store-topaz.vercel.app/${slug}/v${ver}/share?${appId}`;
  navigator.clipboard.writeText(link).then(() => showToast('Share link copied!', 'success')).catch(() => {
    const input = document.createElement('input');
    input.value = link;
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    document.body.removeChild(input);
    showToast('Share link copied!', 'success');
  });
}

// ==================== CATEGORIES ====================

let allCategoriesList = [];

async function loadCategories() {
  try {
    const { data, error } = await db.from('categories').select('*').order('order_index');
    if (error) throw error;
    allCategoriesList = data || [];
    renderCategoriesTable(allCategoriesList);
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

function renderCategoriesTable(cats) {
  const tbody = document.getElementById('categories-tbody');
  if (!cats.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty-row">No categories.</td></tr>';
    return;
  }
  tbody.innerHTML = cats.map(cat => `
    <tr>
      <td>${escHtml(cat.icon || '')}</td>
      <td>${escHtml(cat.name)}</td>
      <td><code>${escHtml(cat.id)}</code></td>
      <td>${cat.app_count || 0}</td>
      <td>${cat.order_index || 0}</td>
      <td class="actions-cell">
        <button class="btn-icon btn-edit" title="Edit" onclick="editCategory('${escAttr(cat.id)}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
        </button>
        <button class="btn-icon btn-delete" title="Delete" onclick="deleteCategory('${escAttr(cat.id)}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </td>
    </tr>`).join('');
}

function filterCategories() {
  const q = document.getElementById('category-search').value.toLowerCase();
  renderCategoriesTable(allCategoriesList.filter(c => c.name.toLowerCase().includes(q) || c.id.toLowerCase().includes(q)));
}

function openAddCategory() {
  editingCategory = null;
  document.getElementById('category-modal-title').textContent = 'Add Category';
  document.getElementById('category-form').reset();
  document.getElementById('category-id-field').disabled = false;
  openModal('category-modal');
}

function editCategory(id) {
  const cat = allCategoriesList.find(c => c.id === id);
  if (!cat) return;
  editingCategory = cat;
  document.getElementById('category-modal-title').textContent = 'Edit Category';
  document.getElementById('category-id-field').value = cat.id;
  document.getElementById('category-id-field').disabled = true;
  document.getElementById('category-name').value = cat.name || '';
  document.getElementById('category-icon').value = cat.icon || '';
  document.getElementById('category-order').value = cat.order_index || 0;
  openModal('category-modal');
}

async function handleCategorySave(e) {
  e.preventDefault();
  const id = document.getElementById('category-id-field').value.trim();
  const isEdit = !!editingCategory;
  if (!id) { showToast('Category ID required', 'error'); return; }
  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  try {
    const catData = { id, name: document.getElementById('category-name').value, icon: document.getElementById('category-icon').value, order_index: parseInt(document.getElementById('category-order').value) || 0 };
    const result = isEdit ? await serviceDb.from('categories').update(catData).eq('id', editingCategory.id) : await serviceDb.from('categories').insert({ ...catData, created_at: new Date().toISOString() });
    if (result.error) throw result.error;
    closeModal('category-modal');
    showToast(isEdit ? 'Updated' : 'Created', 'success');
    loadCategories();
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
  finally { btn.disabled = false; btn.innerHTML = 'Save Category'; }
}

async function deleteCategory(id) {
  if (!confirm('Delete this category?')) return;
  try {
    const { error } = await serviceDb.from('categories').delete().eq('id', id);
    if (error) throw error;
    showToast('Deleted', 'success');
    loadCategories();
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

// ==================== USERS ====================

let allUsers = [];

async function loadUsers() {
  try {
    const { data, error } = await db.from('user_accounts').select('*').order('created_at', { ascending: false });
    if (error) throw error;
    allUsers = data || [];
    renderUsersTable(allUsers);
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

function renderUsersTable(users) {
  const tbody = document.getElementById('users-tbody');
  if (!users.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty-row">No users yet. Click "+ Add User" to create accounts.</td></tr>';
    return;
  }
  tbody.innerHTML = users.map(user => `
    <tr>
      <td>${escHtml(user.name)}</td>
      <td>${escHtml(user.email)}</td>
      <td><code class="password-mask">••••••••</code></td>
      <td><span class="cat-badge">${escHtml(user.role || 'user')}</span></td>
      <td>${user.is_active !== false ? '<span style="color:var(--success)">Active</span>' : '<span style="color:var(--error)">Inactive</span>'}</td>
      <td class="actions-cell">
        <button class="btn-icon btn-edit" title="Edit" onclick="editUser('${user.id}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
        </button>
        <button class="btn-icon btn-delete" title="Delete" onclick="deleteUser('${user.id}')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </td>
    </tr>`).join('');
}

function filterUsers() {
  const q = document.getElementById('user-search').value.toLowerCase();
  renderUsersTable(allUsers.filter(u => u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)));
}

function openAddUser() {
  editingUser = null;
  document.getElementById('user-modal-title').textContent = 'Add User';
  document.getElementById('user-form').reset();
  document.getElementById('user-id').value = '';
  document.getElementById('pw-hint').textContent = '(required)';
  openModal('user-modal');
}

function editUser(id) {
  const user = allUsers.find(u => u.id === id);
  if (!user) return;
  editingUser = user;
  document.getElementById('user-modal-title').textContent = 'Edit User';
  document.getElementById('user-id').value = user.id;
  document.getElementById('user-name').value = user.name || '';
  document.getElementById('user-email').value = user.email || '';
  document.getElementById('user-password').value = '';
  document.getElementById('user-role').value = user.role || 'user';
  document.getElementById('pw-hint').textContent = '(leave blank to keep current)';
  openModal('user-modal');
}

async function handleUserSave(e) {
  e.preventDefault();
  const id = document.getElementById('user-id').value;
  const isEdit = !!id;
  const email = document.getElementById('user-email').value.trim().toLowerCase();
  const password = document.getElementById('user-password').value;
  const name = document.getElementById('user-name').value.trim();
  const role = document.getElementById('user-role').value;

  const emailCheck = SupabaseConfig.validateEmail(email);
  if (!emailCheck.valid) { showToast(emailCheck.error, 'error'); return; }
  if (!isEdit && !password) { showToast('Password is required', 'error'); return; }

  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  try {
    const userData = { name, email, role, is_active: true, updated_at: new Date().toISOString() };
    if (password) userData.password_hash = password;

    let result;
    if (isEdit) { result = await serviceDb.from('user_accounts').update(userData).eq('id', id); }
    else { userData.created_at = new Date().toISOString(); result = await serviceDb.from('user_accounts').insert(userData); }
    if (result.error) throw result.error;
    closeModal('user-modal');
    showToast(isEdit ? 'User updated' : 'User created', 'success');
    loadUsers();
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
  finally { btn.disabled = false; btn.innerHTML = 'Save User'; }
}

async function deleteUser(id) {
  if (!confirm('Delete this user?')) return;
  try {
    const { error } = await serviceDb.from('user_accounts').delete().eq('id', id);
    if (error) throw error;
    showToast('Deleted', 'success');
    loadUsers();
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

// ==================== SUPABASE ACCOUNTS ====================

function renderAccountsList() {
  const container = document.getElementById('accounts-list');
  const accounts = SupabaseConfig.getAccounts();
  const activeId = SupabaseConfig.getActiveAccountId();

  if (!accounts.length) {
    container.innerHTML = '<p class="empty-text">No accounts added yet. Click "+ Add Account" to add a Supabase project.</p>';
    return;
  }

  container.innerHTML = accounts.map(acc => {
    const isActive = acc.id === activeId;
    const isOver = (acc.currentUsers || 0) >= acc.maxUsers;
    const pct = Math.min(100, Math.round(((acc.currentUsers || 0) / acc.maxUsers) * 100));
    const remaining = Math.max(0, acc.maxUsers - (acc.currentUsers || 0));
    const usageClass = pct >= 90 ? 'usage-over' : pct >= 70 ? 'usage-high' : pct >= 40 ? 'usage-medium' : '';
    const remainingPct = Math.max(0, 100 - pct);

    return `
      <div class="account-card ${isActive ? 'account-active' : ''} ${isOver ? 'account-over-limit' : ''}">
        <div class="account-header">
          <div class="account-info">
            <div class="account-name">${escHtml(acc.name)}</div>
            ${acc.email ? `<div class="account-email">${escHtml(acc.email)}</div>` : ''}
            <div class="account-url">${escHtml(acc.url)}</div>
          </div>
          <span class="storage-card-status ${isActive ? 'status-active' : 'status-inactive'}">
            ${isActive ? '● Active' : '○ Inactive'}
          </span>
        </div>
        <div class="account-usage">
          <div class="storage-usage-header">
            <span class="storage-usage-label">Users</span>
            <span class="storage-usage-value">${acc.currentUsers || 0} / ${acc.maxUsers} (${pct}%)</span>
          </div>
          <div class="storage-usage-bar">
            <div class="storage-usage-fill ${usageClass || 'usage-low'}" style="width:${pct}%"></div>
          </div>
          <div style="display:flex;justify-content:space-between;margin-top:4px">
            <span style="font-size:10px;color:var(--text-muted)">${remaining} slots remaining</span>
            <span style="font-size:10px;color:var(--text-muted)">${remainingPct}% free</span>
          </div>
        </div>
        <div class="storage-toggle-wrap">
          <label class="storage-toggle">
            <input type="checkbox" ${isActive ? 'checked' : ''} ${isOver && !isActive ? 'disabled' : ''} onchange="handleToggleAccount('${acc.id}')">
            <span class="storage-toggle-slider"></span>
          </label>
          <span class="storage-toggle-label">${isActive ? 'Active (serving traffic)' : isOver ? 'Limit reached' : 'Inactive'}</span>
        </div>
        <div class="account-actions">
          <button class="btn btn-sm btn-secondary" onclick="editAccount('${acc.id}')">Edit</button>
          <button class="btn btn-sm btn-danger" onclick="deleteAccount('${acc.id}')">Delete</button>
        </div>
      </div>`;
  }).join('');
}

function openAddAccount() {
  document.getElementById('add-account-form').reset();
  document.getElementById('acc-max-users').value = '100';
  openModal('add-account-modal');
}

async function handleAddAccount(e) {
  e.preventDefault();
  const name = document.getElementById('acc-name').value.trim();
  const email = document.getElementById('acc-email').value.trim();
  const url = document.getElementById('acc-url').value.trim();
  const anonKey = document.getElementById('acc-anon-key').value.trim();
  const serviceKey = document.getElementById('acc-service-key').value.trim();
  const maxUsers = parseInt(document.getElementById('acc-max-users').value) || 100;

  if (!name || !url || !anonKey) { showToast('Name, URL and Anon Key are required', 'error'); return; }

  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Testing...';

  try {
    const testClient = supabase.createClient(url, anonKey);
    const { error } = await testClient.from('apps').select('id').limit(1);
    if (error && !error.message.includes('0 rows') && !error.message.includes('does not exist')) throw error;
    SupabaseConfig.addAccount({ name, email, url, anonKey, serviceKey, maxUsers });
    closeModal('add-account-modal');
    renderAccountsList();
    showToast('Account added!', 'success');
  } catch (err) { showToast('Connection failed: ' + err.message, 'error'); }
  finally { btn.disabled = false; btn.innerHTML = 'Add Account'; }
}

function handleToggleAccount(id) {
  const accounts = SupabaseConfig.getAccounts();
  const acc = accounts.find(a => a.id === id);
  if (!acc) return;

  const isActive = acc.isActive;

  if (isActive) {
    const activeCount = accounts.filter(a => a.isActive && a.id !== id).length;
    if (activeCount === 0) {
      showToast('At least one account must be active!', 'warning');
      renderAccountsList();
      return;
    }
    acc.isActive = false;
    SupabaseConfig.saveAccounts(accounts);
    if (SupabaseConfig.getActiveAccountId() === id) {
      const next = accounts.find(a => a.isActive && a.id !== id);
      SupabaseConfig.setActiveAccountId(next ? next.id : '');
    }
  } else {
    if (acc.currentUsers >= acc.maxUsers) {
      showToast(`Max users (${acc.maxUsers}) reached! Cannot activate.`, 'error');
      renderAccountsList();
      return;
    }
    accounts.forEach(a => a.isActive = false);
    acc.isActive = true;
    SupabaseConfig.saveAccounts(accounts);
    SupabaseConfig.setActiveAccountId(id);
  }

  renderAccountsList();
}

function editAccount(id) {
  const accounts = SupabaseConfig.getAccounts();
  const acc = accounts.find(a => a.id === id);
  if (!acc) return;
  document.getElementById('edit-acc-id').value = acc.id;
  document.getElementById('edit-acc-name').value = acc.name;
  document.getElementById('edit-acc-email').value = acc.email || '';
  document.getElementById('edit-acc-url').value = acc.url;
  document.getElementById('edit-acc-anon-key').value = acc.anonKey;
  document.getElementById('edit-acc-service-key').value = acc.serviceKey || '';
  document.getElementById('edit-acc-max-users').value = acc.maxUsers;
  openModal('edit-account-modal');
}

function handleEditAccount(e) {
  e.preventDefault();
  const id = document.getElementById('edit-acc-id').value;
  SupabaseConfig.updateAccount(id, {
    name: document.getElementById('edit-acc-name').value.trim(),
    email: document.getElementById('edit-acc-email').value.trim(),
    url: document.getElementById('edit-acc-url').value.trim(),
    anonKey: document.getElementById('edit-acc-anon-key').value.trim(),
    serviceKey: document.getElementById('edit-acc-service-key').value.trim(),
    maxUsers: parseInt(document.getElementById('edit-acc-max-users').value) || 100
  });
  closeModal('edit-account-modal');
  renderAccountsList();
  showToast('Account updated', 'success');
}

function deleteAccount(id) {
  if (!confirm('Delete this account?')) return;
  SupabaseConfig.deleteAccount(id);
  renderAccountsList();
  showToast('Account deleted', 'success');
}

// ==================== USERS SQL ====================

const USERS_SQL = `-- APK Store - Database Tables
-- ========================================
-- MIGRATION (run if tables already exist):
-- ALTER TABLE app_updates ADD COLUMN IF NOT EXISTS package_name TEXT DEFAULT 'com.apps.apkstore';
-- UPDATE app_updates SET package_name = 'com.apps.apkstore' WHERE package_name = 'com.zoroapps.appstore';
-- ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS display_name TEXT DEFAULT '';
-- ALTER TABLE user_accounts ADD COLUMN IF NOT EXISTS device_id TEXT DEFAULT '';
-- ========================================
-- Run this in your Supabase SQL Editor

-- Apps table
CREATE TABLE IF NOT EXISTS apps (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  package_name TEXT UNIQUE NOT NULL,
  version_name TEXT DEFAULT '1.0.0',
  version_code INTEGER DEFAULT 1,
  short_description TEXT DEFAULT '',
  description TEXT DEFAULT '',
  category TEXT DEFAULT 'General',
  icon_url TEXT DEFAULT '',
  banner_url TEXT DEFAULT '',
  screenshot_urls TEXT[] DEFAULT '{}',
  download_url TEXT DEFAULT '',
  file_size BIGINT DEFAULT 0,
  min_sdk INTEGER DEFAULT 21,
  target_sdk INTEGER DEFAULT 34,
  rating REAL DEFAULT 0,
  review_count INTEGER DEFAULT 0,
  download_count INTEGER DEFAULT 0,
  developer_name TEXT DEFAULT '',
  developer_email TEXT DEFAULT '',
  release_date TEXT DEFAULT '',
  last_updated TEXT DEFAULT '',
  is_featured BOOLEAN DEFAULT FALSE,
  is_new BOOLEAN DEFAULT FALSE,
  is_updated BOOLEAN DEFAULT FALSE,
  is_verified BOOLEAN DEFAULT FALSE,
  is_share_only BOOLEAN DEFAULT FALSE,
  tags TEXT[] DEFAULT '{}',
  permissions TEXT[] DEFAULT '{}',
  whats_new TEXT DEFAULT '',
  changelog TEXT DEFAULT '',
  signature_hash TEXT DEFAULT '',
  signing_cert TEXT DEFAULT '',
  upload_status TEXT DEFAULT 'pending' CHECK (upload_status IN ('pending', 'uploading', 'uploaded', 'failed', 'linked')),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Categories table
CREATE TABLE IF NOT EXISTS categories (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  icon TEXT DEFAULT '📦',
  sort_order INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- User accounts (admin panel login + Android app auth)
CREATE TABLE IF NOT EXISTS user_accounts (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  display_name TEXT DEFAULT '',
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT DEFAULT 'user' CHECK (role IN ('admin', 'developer', 'user')),
  is_active BOOLEAN DEFAULT TRUE,
  device_id TEXT DEFAULT '',
  last_login TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- App updates table
CREATE TABLE IF NOT EXISTS app_updates (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  package_name TEXT NOT NULL,
  version_name TEXT NOT NULL,
  version_code INTEGER NOT NULL,
  apk_url TEXT DEFAULT '',
  release_notes TEXT DEFAULT '',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Device registrations
CREATE TABLE IF NOT EXISTS device_registrations (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES user_accounts(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  fcm_token TEXT,
  device_model TEXT,
  android_version TEXT,
  app_version TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  last_seen TIMESTAMPTZ DEFAULT NOW(),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_apps_package ON apps(package_name);
CREATE INDEX IF NOT EXISTS idx_apps_category ON apps(category);
CREATE INDEX IF NOT EXISTS idx_updates_package ON app_updates(package_name);
CREATE INDEX IF NOT EXISTS idx_user_accounts_email ON user_accounts(email);
CREATE INDEX IF NOT EXISTS idx_device_registrations_device ON device_registrations(device_id);

-- Add upload_status column if not exists
DO $$ BEGIN
  ALTER TABLE apps ADD COLUMN upload_status TEXT DEFAULT 'pending' CHECK (upload_status IN ('pending', 'uploading', 'uploaded', 'failed', 'linked'));
EXCEPTION WHEN duplicate_column THEN END;
$$;

-- Add upload_source column if not exists
DO $$ BEGIN
  ALTER TABLE apps ADD COLUMN upload_source TEXT DEFAULT '';
EXCEPTION WHEN duplicate_column THEN END;
$$;

-- Auth accounts table (for Android app to detect active auth Supabase)
CREATE TABLE IF NOT EXISTS supabase_accounts (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT DEFAULT '',
  sup_url TEXT NOT NULL,
  sup_anon_key TEXT NOT NULL,
  sup_service_key TEXT DEFAULT '',
  max_users INTEGER DEFAULT 100,
  current_users INTEGER DEFAULT 0,
  is_active BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- RLS: allow public read (Android app needs anon read), service role write
ALTER TABLE supabase_accounts ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  DROP POLICY IF EXISTS "Public read accounts" ON supabase_accounts;
EXCEPTION WHEN OTHERS THEN END;
$$;
CREATE POLICY "Public read accounts" ON supabase_accounts FOR SELECT USING (true);
DO $$ BEGIN
  DROP POLICY IF EXISTS "Service write accounts" ON supabase_accounts;
EXCEPTION WHEN OTHERS THEN END;
$$;
CREATE POLICY "Service write accounts" ON supabase_accounts FOR ALL USING (true);`;

function showUsersSQL() {
  document.getElementById('sql-code').textContent = USERS_SQL;
  openModal('sql-modal');
}

function exportAdminConfig() {
  const accounts = SupabaseConfig.getAccounts();
  const activeId = SupabaseConfig.getActiveAccountId();
  if (!accounts.length) {
    showToast('No Supabase accounts found. Add an account first.', 'error');
    return;
  }
  const config = {
    accounts: accounts.map(a => ({
      id: a.id,
      name: a.name,
      url: a.url,
      anonKey: a.anonKey,
      serviceKey: a.serviceKey || '',
      isActive: a.id === activeId && a.isActive,
      currentUsers: a.currentUsers || 0,
      maxUsers: a.maxUsers || 100
    })),
    exportedAt: new Date().toISOString()
  };
  const json = JSON.stringify(config, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'admin_config.json';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  showToast('Config exported! Place this file at: landing-page/admin_config.json', 'success');
}

// ==================== APP UPDATES ====================

let allUpdates = [];
const STORE_PACKAGE = 'com.apps.apkstore';

async function loadUpdates() {
  if (!db) return;
  try {
    const { data, error } = await db.from('app_updates')
      .select('*')
      .eq('package_name', STORE_PACKAGE)
      .order('version_code', { ascending: false });
    if (error) throw error;
    allUpdates = data || [];
    renderUpdatesTable(allUpdates);
  } catch (err) {
    showToast('Failed to load updates: ' + err.message, 'error');
  }
}

function renderUpdatesTable(updates) {
  const tbody = document.getElementById('updates-tbody');
  if (!updates.length) {
    tbody.innerHTML = '<tr><td colspan="5" class="empty-row">No updates yet. Click "+ Push Update" to create one.</td></tr>';
    return;
  }
  tbody.innerHTML = updates.map(upd => {
    const apkDisplay = upd.apk_url ? (upd.apk_url.includes('github.com') ? '🐙 GitHub' : upd.apk_url.includes('pcloud') ? '☁️ pCloud' : upd.apk_url.includes('supabase') ? '📦 Supabase' : upd.apk_url.includes('cloudinary') ? '🖼️ Cloudinary' : '🔗 URL') : '—';
    const date = upd.created_at ? new Date(upd.created_at).toLocaleDateString() : '—';
    return `
      <tr>
        <td><span class="badge badge-new">${escHtml(upd.version_name || '')} (${upd.version_code || 0})</span></td>
        <td>${apkDisplay}</td>
        <td style="max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escHtml(upd.release_notes || '')}</td>
        <td>${date}</td>
        <td class="actions-cell">
          <button class="btn-icon btn-edit" title="Edit" onclick="editUpdate('${upd.id}')">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </button>
          <button class="btn-icon btn-delete" title="Delete" onclick="deleteUpdate('${upd.id}')">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </td>
      </tr>`;
  }).join('');
}

function openAddUpdate() {
  document.getElementById('update-form').reset();
  document.getElementById('update-id').value = '';
  document.getElementById('update-apk-name').textContent = '';
  document.getElementById('update-modal-title').textContent = 'Push Store Update';
  openModal('update-modal');
}

function validateUrl(url) {
  if (!url) return true;
  try { new URL(url); return true; } catch { return false; }
}

async function handleUpdateSave(e) {
  e.preventDefault();
  const id = document.getElementById('update-id').value;
  const isEdit = !!id;
  const apkUrl = document.getElementById('update-apk-url').value.trim();
  const apkFile = document.getElementById('update-apk-input').files[0];

  if (apkUrl && !validateUrl(apkUrl)) {
    showToast('Invalid APK URL', 'error');
    return;
  }
  if (!apkUrl && !apkFile) {
    showToast('Provide APK URL or upload APK file', 'error');
    return;
  }

  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Saving...';

  try {
    let finalUrl = apkUrl;
    if (apkFile) {
      finalUrl = await uploadFile('apps', apkFile);
    }

    const versionName = document.getElementById('update-version-name').value;
    const versionCode = parseInt(document.getElementById('update-version-code').value);
    const releaseNotes = document.getElementById('update-release-notes').value;

    const updateData = {
      package_name: STORE_PACKAGE,
      version_name: versionName,
      version_code: versionCode,
      apk_url: finalUrl,
      release_notes: releaseNotes,
      updated_at: new Date().toISOString()
    };

    let result;
    if (isEdit) {
      result = await serviceDb.from('app_updates').update(updateData).eq('id', id);
    } else {
      updateData.created_at = new Date().toISOString();
      result = await serviceDb.from('app_updates').insert(updateData);
    }
    if (result.error) throw result.error;

    const { error: appErr } = await serviceDb.from('apps')
      .update({ version_name: versionName, version_code: versionCode, download_url: finalUrl, updated_at: new Date().toISOString() })
      .eq('package_name', STORE_PACKAGE);
    if (appErr) console.warn('Could not update apps table:', appErr.message);

    closeModal('update-modal');
    showToast(isEdit ? 'Update pushed!' : 'Update pushed! Users will see the prompt.', 'success');
    loadUpdates();
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
  finally { btn.disabled = false; btn.innerHTML = 'Push Update'; }
}

async function editUpdate(id) {
  try {
    const { data, error } = await db.from('app_updates').select('*').eq('id', id).single();
    if (error) throw error;
    document.getElementById('update-id').value = data.id;
    document.getElementById('update-modal-title').textContent = 'Edit Store Update';
    document.getElementById('update-version-name').value = data.version_name || '';
    document.getElementById('update-version-code').value = data.version_code || '';
    document.getElementById('update-apk-url').value = data.apk_url || '';
    document.getElementById('update-release-notes').value = data.release_notes || '';
    document.getElementById('update-apk-name').textContent = data.apk_url ? 'Current: ' + data.apk_url.split('/').pop() : '';
    openModal('update-modal');
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

async function deleteUpdate(id) {
  if (!confirm('Delete this update?')) return;
  try {
    const { error } = await serviceDb.from('app_updates').delete().eq('id', id);
    if (error) throw error;
    showToast('Deleted', 'success');
    loadUpdates();
  } catch (err) { showToast('Failed: ' + err.message, 'error'); }
}

// ==================== STORAGE SETTINGS ====================

let currentStorageType = 'supabase';
let allStorageConfigs = {};

const STORAGE_CONFIGS = {
  supabase: {
    name: 'Supabase',
    icon: '📦',
    fields: [
      { id: 'sup-name', label: 'Account Name', type: 'text', required: true, placeholder: 'My Supabase Project' },
      { id: 'sup-url', label: 'Project URL', type: 'url', required: true, placeholder: 'https://xxx.supabase.co' },
      { id: 'sup-anon-key', label: 'Anon Key', type: 'text', required: true, placeholder: 'eyJ...' },
      { id: 'sup-service-key', label: 'Service Role Key', type: 'text', required: false, placeholder: 'eyJ...' },
      { id: 'sup-bucket', label: 'Storage Bucket', type: 'text', required: false, placeholder: 'apps' }
    ]
  },
  pcloud: {
    name: 'pCloud',
    icon: '☁️',
    fields: [
      { id: 'pcloud-name', label: 'Account Name', type: 'text', required: true, placeholder: 'My pCloud Account' },
      { id: 'pcloud-email', label: 'Email', type: 'email', required: true, placeholder: 'user@example.com' },
      { id: 'pcloud-token', label: 'OAuth2 Token', type: 'text', required: true, placeholder: 'Your pCloud API token' },
      { id: 'pcloud-folder', label: 'Folder Path', type: 'text', required: false, placeholder: '/AppStore/APKs' },
      { id: 'pcloud-region', label: 'Region', type: 'select', required: true, options: [
        { value: 'us', label: 'United States (api.pcloud.com)' },
        { value: 'eu', label: 'Europe (eapi.pcloud.com)' }
      ]}
    ]
  },
  cloudinary: {
    name: 'Cloudinary',
    icon: '🖼️',
    fields: [
      { id: 'cld-name', label: 'Account Name', type: 'text', required: true, placeholder: 'My Cloudinary' },
      { id: 'cld-cloud-name', label: 'Cloud Name', type: 'text', required: true, placeholder: 'your-cloud-name' },
      { id: 'cld-api-key', label: 'API Key', type: 'text', required: true, placeholder: '1234567890' },
      { id: 'cld-api-secret', label: 'API Secret', type: 'text', required: true, placeholder: 'Your API secret' },
      { id: 'cld-folder', label: 'Folder', type: 'text', required: false, placeholder: 'apk-store' }
    ]
  },
  clorabase: {
    name: 'GitHub Storage',
    icon: '🐙',
    fields: [
      { id: 'cla-name', label: 'Account Name', type: 'text', required: true, placeholder: 'My GitHub Storage' },
      { id: 'cla-repo', label: 'Repository', type: 'text', required: true, placeholder: 'username/repo-name' },
      { id: 'cla-token', label: 'Personal Access Token', type: 'text', required: true, placeholder: 'ghp_xxxxxxxxxxxx' },
      { id: 'cla-branch', label: 'Release Tag', type: 'text', required: false, placeholder: 'v1.0.0' }
    ]
  },
  archiveorg: {
    name: 'Archive.org',
    icon: '🏛️',
    fields: [
      { id: 'ao-name', label: 'Account Name', type: 'text', required: true, placeholder: 'My Archive.org' },
      { id: 'ao-access-key', label: 'S3 Access Key', type: 'text', required: true, placeholder: 'Your S3 access key' },
      { id: 'ao-secret-key', label: 'S3 Secret Key', type: 'text', required: true, placeholder: 'Your S3 secret key' },
      { id: 'ao-item-prefix', label: 'Item Prefix', type: 'text', required: false, placeholder: 'apk-store' }
    ]
  },
  pixeldrain: {
    name: 'Pixeldrain',
    icon: '📥',
    fields: [
      { id: 'pd-name', label: 'Account Name', type: 'text', required: true, placeholder: 'My Pixeldrain' },
      { id: 'pd-api-key', label: 'API Key (optional)', type: 'text', required: false, placeholder: 'Leave empty for anonymous upload' }
    ]
  }
};

function loadStorageConfigs() {
  try {
    const saved = localStorage.getItem('zoro_storage_configs');
    allStorageConfigs = saved ? JSON.parse(saved) : { supabase: [], pcloud: [], cloudinary: [], clorabase: [], archiveorg: [], pixeldrain: [] };
    if (!allStorageConfigs.supabase) allStorageConfigs.supabase = [];
    if (!allStorageConfigs.pcloud) allStorageConfigs.pcloud = [];
    if (!allStorageConfigs.cloudinary) allStorageConfigs.cloudinary = [];
    if (!allStorageConfigs.clorabase) allStorageConfigs.clorabase = [];
    if (!allStorageConfigs.archiveorg) allStorageConfigs.archiveorg = [];
    if (!allStorageConfigs.pixeldrain) allStorageConfigs.pixeldrain = [];
    if (!allStorageConfigs.clorabase.length && !allStorageConfigs.supabase.find(a => a.isActive) && !allStorageConfigs.cloudinary.find(a => a.isActive) && !allStorageConfigs.pcloud.find(a => a.isActive) && !allStorageConfigs.archiveorg.find(a => a.isActive) && !allStorageConfigs.pixeldrain.find(a => a.isActive)) {
      allStorageConfigs.clorabase = [{
        id: 'gh-default', name: 'GitHub DMRS-APK', isActive: true,
        'cla-repo': 'Giti12387/DMRS-APK', 'cla-token': 'YOUR_GITHUB_TOKEN_HERE', 'cla-branch': 'release-' + Date.now()
      }];
      saveStorageConfigs();
    }
  } catch {
    allStorageConfigs = { supabase: [], pcloud: [], cloudinary: [], clorabase: [], archiveorg: [], pixeldrain: [] };
  }
}

function saveStorageConfigs() {
  localStorage.setItem('zoro_storage_configs', JSON.stringify(allStorageConfigs));
}

function switchStorage(type) {
  currentStorageType = type;
  document.querySelectorAll('.storage-tab').forEach(t => t.classList.remove('active'));
  document.querySelector(`.storage-tab[data-storage="${type}"]`).classList.add('active');
  document.querySelectorAll('.storage-form').forEach(f => f.classList.remove('active'));
  document.getElementById(`form-${type}`).classList.add('active');
  renderStorageList(type);
}

function renderStorageList(type) {
  loadStorageConfigs();
  const container = document.getElementById(`storage-list-${type}`);
  const configs = allStorageConfigs[type] || [];

  if (!configs.length) {
    container.innerHTML = `
      <div class="storage-empty">
        <div class="storage-empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>
        </div>
        <p>No ${STORAGE_CONFIGS[type].name} accounts configured yet.</p>
        <p style="margin-top:4px;font-size:12px">Click "Add Account" to get started.</p>
      </div>`;
    return;
  }

  container.innerHTML = configs.map(config => {
    const limit = config.storageLimit || 0;
    const usage = config.usageMB || 0;
    const pct = limit > 0 ? Math.min((usage / limit) * 100, 100) : 0;
    const usageClass = pct >= 90 ? 'usage-critical' : pct >= 70 ? 'usage-high' : pct >= 40 ? 'usage-medium' : 'usage-low';
    const limitLabel = limit > 0 ? `${usage.toFixed(1)} / ${limit} MB (${pct.toFixed(0)}%)` : `${usage.toFixed(1)} MB used (unlimited)`;
    const remaining = limit > 0 ? (limit - usage).toFixed(1) : '∞';
    const remainingPct = limit > 0 ? ((1 - usage / limit) * 100).toFixed(0) : '100';

    return `
    <div class="storage-card ${config.isActive ? 'storage-active' : ''}">
      <div class="storage-card-header">
        <div class="storage-card-info">
          <div class="storage-card-name">${escHtml(config.name)}</div>
          <div class="storage-card-detail">${escHtml(config.detail || config.url || config.email || 'Configured')}</div>
        </div>
        <span class="storage-card-status ${config.isActive ? 'status-active' : 'status-inactive'}">
          ${config.isActive ? '● Active' : '○ Inactive'}
        </span>
      </div>
      <div class="storage-usage">
        <div class="storage-usage-header">
          <span class="storage-usage-label">Usage</span>
          <span class="storage-usage-value">${limitLabel}</span>
        </div>
        <div class="storage-usage-bar">
          <div class="storage-usage-fill ${limit > 0 ? usageClass : 'usage-low'}" style="width:${limit > 0 ? pct : Math.min(usage, 100)}%"></div>
        </div>
        <div style="display:flex;justify-content:space-between;margin-top:4px">
          <span style="font-size:10px;color:var(--text-muted)">${remaining} MB remaining</span>
          <span style="font-size:10px;color:var(--text-muted)">${remainingPct}% free</span>
        </div>
      </div>
      <div class="storage-toggle-wrap">
        <label class="storage-toggle">
          <input type="checkbox" ${config.isActive ? 'checked' : ''} onchange="toggleStorageActive('${type}', '${config.id}', this.checked)">
          <span class="storage-toggle-slider"></span>
        </label>
        <span class="storage-toggle-label">${config.isActive ? 'Active (serving traffic)' : 'Inactive'}</span>
      </div>
      <div class="storage-card-actions">
        <button class="btn btn-sm btn-secondary" onclick="editStorage('${type}', '${config.id}')">Edit</button>
        <button class="btn btn-sm btn-danger" onclick="deleteStorage('${type}', '${config.id}')">Delete</button>
      </div>
    </div>`;
  }).join('');
}

function openAddStorageModal(type) {
  loadStorageConfigs();
  const config = STORAGE_CONFIGS[type];
  document.getElementById('storage-type').value = type;
  document.getElementById('storage-edit-id').value = '';
  document.getElementById('storage-modal-title').textContent = `Add ${config.name} Account`;
  document.getElementById('storage-submit-btn').textContent = 'Add Storage';
  document.getElementById('storage-limit').value = '0';

  const fieldsContainer = document.getElementById('storage-fields');
  fieldsContainer.innerHTML = config.fields.map(field => {
    if (field.type === 'select') {
      return `
        <div class="form-group">
          <label>${field.label} ${field.required ? '<span class="req">*</span>' : ''}</label>
          <select id="${field.id}" ${field.required ? 'required' : ''}>
            ${field.options.map(opt => `<option value="${opt.value}">${opt.label}</option>`).join('')}
          </select>
        </div>`;
    }
    return `
      <div class="form-group">
        <label>${field.label} ${field.required ? '<span class="req">*</span>' : ''}</label>
        <input type="${field.type}" id="${field.id}" ${field.required ? 'required' : ''} placeholder="${field.placeholder || ''}">
      </div>`;
  }).join('');

  openModal('storage-modal');
}

function editStorage(type, id) {
  loadStorageConfigs();
  const configs = allStorageConfigs[type] || [];
  const config = configs.find(c => c.id === id);
  if (!config) return;

  const storageConfig = STORAGE_CONFIGS[type];
  document.getElementById('storage-type').value = type;
  document.getElementById('storage-edit-id').value = id;
  document.getElementById('storage-modal-title').textContent = `Edit ${storageConfig.name} Account`;
  document.getElementById('storage-submit-btn').textContent = 'Update Storage';
  document.getElementById('storage-limit').value = config.storageLimit || 0;

  const fieldsContainer = document.getElementById('storage-fields');
  fieldsContainer.innerHTML = storageConfig.fields.map(field => {
    const value = config[field.id] || '';
    if (field.type === 'select') {
      return `
        <div class="form-group">
          <label>${field.label} ${field.required ? '<span class="req">*</span>' : ''}</label>
          <select id="${field.id}" ${field.required ? 'required' : ''}>
            ${field.options.map(opt => `<option value="${opt.value}" ${opt.value === value ? 'selected' : ''}>${opt.label}</option>`).join('')}
          </select>
        </div>`;
    }
    return `
      <div class="form-group">
        <label>${field.label} ${field.required ? '<span class="req">*</span>' : ''}</label>
        <input type="${field.type}" id="${field.id}" ${field.required ? 'required' : ''} placeholder="${field.placeholder || ''}" value="${escAttr(value)}">
      </div>`;
  }).join('');

  openModal('storage-modal');
}

document.getElementById('storage-form').addEventListener('submit', function(e) {
  e.preventDefault();
  const type = document.getElementById('storage-type').value;
  const editId = document.getElementById('storage-edit-id').value;
  const isEdit = !!editId;
  const storageConfig = STORAGE_CONFIGS[type];
  const storageLimit = parseInt(document.getElementById('storage-limit').value) || 0;

  const data = { id: editId || `stor_${Date.now()}_${Math.random().toString(36).slice(2, 6)}` };
  let isValid = true;

  storageConfig.fields.forEach(field => {
    const el = document.getElementById(field.id);
    const value = el.value.trim();
    if (field.required && !value) {
      showToast(`${field.label} is required`, 'error');
      isValid = false;
      return;
    }
    data[field.id] = value;
  });

  if (!isValid) return;

  data.name = data[storageConfig.fields[0].id] || 'Unnamed';
  data.detail = data[storageConfig.fields[1].id] || '';
  data.url = data[storageConfig.fields[1].id] || '';
  data.email = data[storageConfig.fields[1].id] || '';
  data.isActive = false;
  data.storageLimit = storageLimit;
  data.usageMB = 0;
  data.createdAt = new Date().toISOString();

  loadStorageConfigs();
  if (isEdit) {
    const idx = allStorageConfigs[type].findIndex(c => c.id === editId);
    if (idx !== -1) {
      data.isActive = allStorageConfigs[type][idx].isActive;
      data.usageMB = allStorageConfigs[type][idx].usageMB || 0;
      allStorageConfigs[type][idx] = data;
    }
  } else {
    allStorageConfigs[type].push(data);
  }
  saveStorageConfigs();
  renderStorageList(type);
  closeModal('storage-modal');
  showToast(isEdit ? 'Storage updated!' : 'Storage added!', 'success');
});

function toggleStorageActive(type, id, checked) {
  loadStorageConfigs();
  const configs = allStorageConfigs[type] || [];

  if (checked) {
    configs.forEach(c => c.isActive = (c.id === id));
  } else {
    const target = configs.find(c => c.id === id);
    const activeCount = configs.filter(c => c.isActive && c.id !== id).length;
    if (activeCount === 0) {
      showToast('At least one account must be active!', 'warning');
      renderStorageList(type);
      return;
    }
    if (target) target.isActive = false;
  }

  saveStorageConfigs();
  renderStorageList(type);
  showToast('Storage updated', 'success');
}

function deleteStorage(type, id) {
  if (!confirm('Delete this storage account?')) return;
  loadStorageConfigs();
  allStorageConfigs[type] = (allStorageConfigs[type] || []).filter(c => c.id !== id);
  saveStorageConfigs();
  renderStorageList(type);
  showToast('Storage deleted', 'success');
}

// Initialize storage on page load
loadStorageConfigs();

// ==================== SUPABASE SQL ====================

const SUPABASE_SQL = `-- APK Store - Storage Buckets
-- Run this in your Supabase SQL Editor

-- Create storage buckets (ignore if already exists)
INSERT INTO storage.buckets (id, name, public) VALUES
  ('apps', 'apps', true),
  ('icons', 'icons', true),
  ('banners', 'banners', true),
  ('screenshots', 'screenshots', true)
ON CONFLICT (id) DO NOTHING;

-- Drop old policies if they exist, then recreate
DROP POLICY IF EXISTS "Public read access" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated upload" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated update" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated delete" ON storage.objects;

-- Allow public read access to buckets
CREATE POLICY "Public read access" ON storage.objects
  FOR SELECT USING (bucket_id IN ('apps', 'icons', 'banners', 'screenshots'));

-- Allow authenticated insert/update/delete
CREATE POLICY "Authenticated upload" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id IN ('apps', 'icons', 'banners', 'screenshots'));

CREATE POLICY "Authenticated update" ON storage.objects
  FOR UPDATE USING (bucket_id IN ('apps', 'icons', 'banners', 'screenshots'));

CREATE POLICY "Authenticated delete" ON storage.objects
  FOR DELETE USING (bucket_id IN ('apps', 'icons', 'banners', 'screenshots'));`;

function showSupabaseSQL() {
  document.getElementById('sql-code').textContent = SUPABASE_SQL;
  openModal('sql-modal');
}

function copySqlToClipboard() {
  const sqlText = document.getElementById('sql-code').textContent;
  navigator.clipboard.writeText(sqlText).then(() => {
    const btn = document.querySelector('.sql-copy-btn');
    btn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg> Copied!';
    setTimeout(() => {
      btn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy';
    }, 2000);
    showToast('SQL copied to clipboard!', 'success');
  }).catch(() => {
    const textarea = document.createElement('textarea');
    textarea.value = sqlText;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    showToast('SQL copied to clipboard!', 'success');
  });
}

// ==================== STORAGE PING / KEEP-ALIVE ====================

let pingInterval = null;

function startStoragePing() {
  if (pingInterval) clearInterval(pingInterval);
  pingInterval = setInterval(() => {
    pingAllStorageAccounts();
    pingSupabaseAccounts();
    pingGitHubAccounts();
  }, 30 * 60 * 1000);
  pingAllStorageAccounts();
  pingSupabaseAccounts();
  pingGitHubAccounts();
}

function pingSupabaseAccounts() {
  const accounts = SupabaseConfig.getAccounts();
  accounts.forEach(acc => {
    if (!acc.isActive) return;
    try {
      const url = acc.url.replace(/\/$/, '');
      fetch(`${url}/rest/v1/?select=count`, {
        headers: {
          'apikey': acc.anonKey,
          'Authorization': `Bearer ${acc.anonKey}`
        }
      }).then(res => {
        if (res.ok) {
          acc.lastPinged = new Date().toISOString();
          acc.pingStatus = 'ok';
          SupabaseConfig.saveAccounts(accounts);
        }
      }).catch(() => {});
    } catch (e) {}
  });
}

function pingGitHubAccounts() {
  loadStorageConfigs();
  const ghAccounts = allStorageConfigs.clorabase || [];
  ghAccounts.forEach(acc => {
    if (!acc.isActive) return;
    const repo = acc['cla-repo'];
    const token = acc['cla-token'];
    if (!repo || !token) return;
    fetch(`https://api.github.com/repos/${repo}`, {
      headers: { 'Authorization': `token ${token}`, 'Accept': 'application/vnd.github.v3+json' }
    }).then(res => {
      if (res.ok) {
        acc.lastPinged = new Date().toISOString();
        acc.pingStatus = 'ok';
        acc.usageMB = acc.usageMB || 0;
        saveStorageConfigs();
      }
    }).catch(() => {});
  });
}

function pingAllStorageAccounts() {
  loadStorageConfigs();
  const allTypes = ['supabase', 'pcloud', 'cloudinary', 'clorabase'];

  allTypes.forEach(type => {
    const configs = allStorageConfigs[type] || [];
    configs.forEach(config => {
      if (!config.isActive) return;
      pingAccount(type, config);
    });
  });
}

async function pingAccount(type, config) {
  try {
    if (type === 'supabase' && config['sup-url'] && config['sup-anon-key']) {
      const url = config['sup-url'].replace(/\/$/, '');
      const response = await fetch(`${url}/rest/v1/?select=count`, {
        headers: {
          'apikey': config['sup-anon-key'],
          'Authorization': `Bearer ${config['sup-anon-key']}`
        }
      });
      if (response.ok) {
        config.lastPinged = new Date().toISOString();
        config.pingStatus = 'ok';
      } else {
        config.pingStatus = 'error';
      }
    } else if (type === 'cloudinary' && config['cld-cloud-name'] && config['cld-api-key']) {
      const response = await fetch(`https://api.cloudinary.com/v1_1/${config['cld-cloud-name']}/resources/image?type=upload&max_results=1`, {
        headers: {
          'Authorization': `Basic ${btoa(config['cld-api-key'] + ':' + config['cld-api-secret'])}`
        }
      });
      if (response.ok) {
        config.lastPinged = new Date().toISOString();
        config.pingStatus = 'ok';
      } else {
        config.pingStatus = 'error';
      }
    } else if (type === 'pcloud' && config['pcloud-token']) {
      const region = config['pcloud-region'] === 'eu' ? 'eapi' : 'api';
      const response = await fetch(`https://${region}.pcloud.com/userinfo`, {
        headers: { 'Authorization': `Bearer ${config['pcloud-token']}` }
      });
      if (response.ok) {
        config.lastPinged = new Date().toISOString();
        config.pingStatus = 'ok';
        const data = await response.json();
        if (data.usedquota) {
          config.usageMB = Math.round(data.usedquota / (1024 * 1024));
        }
      } else {
        config.pingStatus = 'error';
      }
    }
  } catch (e) {
    config.pingStatus = 'error';
  }
  saveStorageConfigs();
}

function autoSwitchToFreeAccount(exhaustedConfig) {
  const type = Object.keys(allStorageConfigs).find(t =>
    (allStorageConfigs[t] || []).some(c => c.id === exhaustedConfig.id)
  );
  if (!type) return;

  const configs = allStorageConfigs[type] || [];
  const freeAccount = configs.find(c => {
    if (c.id === exhaustedConfig.id) return false;
    const limit = c.storageLimit || 0;
    const usage = c.usageMB || 0;
    if (limit === 0) return true;
    const remaining = ((limit - usage) / limit) * 100;
    return remaining > 10;
  });

  if (freeAccount) {
    exhaustedConfig.isActive = false;
    freeAccount.isActive = true;
    saveStorageConfigs();
    renderStorageList(type);
    showToast(`Auto-switched to "${freeAccount.name}" - limit reached on "${exhaustedConfig.name}"`, 'warning');
  }
}

startStoragePing();

// ==================== UTILITIES ====================

function openModal(id) {
  document.getElementById(id).style.display = 'flex';
  document.body.style.overflow = 'hidden';
}

function closeModal(id) {
  document.getElementById(id).style.display = 'none';
  document.body.style.overflow = '';
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  const icons = { success: '✅', error: '❌', info: 'ℹ️', warning: '⚠️' };
  toast.innerHTML = `<span class="toast-icon">${icons[type] || 'ℹ️'}</span><span class="toast-msg">${escHtml(message)}</span>`;
  container.appendChild(toast);
  setTimeout(() => toast.classList.add('show'), 10);
  setTimeout(() => { toast.classList.remove('show'); setTimeout(() => toast.remove(), 300); }, 4000);
}

function formatNumber(n) {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
  return String(n);
}

function formatSize(bytes) {
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return bytes + ' B';
}

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString();
}

function previewImage(e, imgId) {
  const file = e.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = ev => {
    const img = document.getElementById(imgId);
    img.src = ev.target.result;
    img.style.display = 'block';
  };
  reader.readAsDataURL(file);
}

function escHtml(str) {
  if (!str) return '';
  const el = document.createElement('span');
  el.textContent = String(str);
  return el.innerHTML;
}

function escAttr(str) {
  return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
}
